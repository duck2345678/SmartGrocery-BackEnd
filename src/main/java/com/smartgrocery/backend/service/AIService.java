package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.ChatMessage;
import com.smartgrocery.backend.entity.ChatSession;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.ChatMessageRepository;
import com.smartgrocery.backend.repository.ChatSessionRepository;
import com.smartgrocery.backend.repository.UserRepository;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AIService {

    @Autowired
    private ProductNodeRepository productNodeRepository;

    @Autowired
    private GeminiAIService geminiAIService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.smartgrocery.backend.repository.UserNutritionProfileRepository userNutritionProfileRepository;

    public Mono<String> getAIResponse(String userQuery, Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        List<ChatMessage> history = chatMessageRepository.findByChatSession_IdOrderByCreatedAtAsc(sessionId);

        // Bước 1: Lấy context thông minh từ Neo4j dựa trên từ khóa trong query
        // Trong thực tế có thể dùng NLP để trích xuất, ở đây demo đơn giản bằng cách tách chuỗi
        String keyword = extractKeyword(userQuery);
        List<ProductNode> relevantProducts = productNodeRepository.searchByKeyword(keyword);
        
        // Nếu không tìm thấy theo keyword, thử tìm theo thành phần
        if (relevantProducts.isEmpty()) {
            relevantProducts = productNodeRepository.findByIngredient(keyword);
        }

        // Nếu vẫn trống thì mới lấy top sản phẩm chung
        if (relevantProducts.isEmpty()) {
            relevantProducts = productNodeRepository.findAll().stream().limit(5).collect(Collectors.toList());
        }

        String context = relevantProducts.stream()
                .limit(10)
                .map(p -> String.format("- %s (Giá: %.0f VND)", p.getName(), p.getPrice()))
                .collect(Collectors.joining("\n"));

        // Bước 2: Tạo prompt chuyên nghiệp hơn
        String prompt = String.format("""
                Bối cảnh hệ thống: Bạn là chuyên gia tư vấn mua sắm tại siêu thị SmartGrocery.
                Nhiệm vụ: Trả lời câu hỏi của khách hàng dựa trên danh sách sản phẩm thực tế từ kho hàng.
                
                Danh sách sản phẩm liên quan trong kho:
                %s
                
                Yêu cầu:
                1. Trả lời ngắn gọn, lịch sự, tập trung vào các sản phẩm có trong danh sách trên.
                2. Nếu khách hỏi về giá, hãy nêu chính xác giá từ context.
                3. Nếu không có sản phẩm nào phù hợp trong context, hãy đề xuất khách hàng liên hệ nhân viên hoặc thử tìm từ khóa khác.
                4. Luôn giữ phong thái chuyên nghiệp và thân thiện.

                Câu hỏi của khách hàng: "%s"
                """, context, userQuery);

        // Bước 3: Lưu tin nhắn người dùng
        ChatMessage userMsg = ChatMessage.builder()
                .chatSession(session)
                .role("USER")
                .content(userQuery)
                .build();
        chatMessageRepository.save(userMsg);

        // Bước 4: Gửi prompt đến Gemini AI
        return geminiAIService.getGeminiResponse(prompt, history)
                .flatMap(aiResponse -> {
                    // Bước 5: Lưu phản hồi của AI
                    ChatMessage assistantMsg = ChatMessage.builder()
                            .chatSession(session)
                            .role("ASSISTANT")
                            .content(aiResponse)
                            .build();
                    chatMessageRepository.save(assistantMsg);
                    return Mono.just(aiResponse);
                });
    }

    private String extractKeyword(String query) {
        // Logic đơn giản: lấy từ dài nhất trong câu hỏi làm từ khóa tìm kiếm
        String[] words = query.replaceAll("[^a-zA-Z0-9\\sàáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]", "").split("\\s+");
        String keyword = words[0];
        for (String w : words) {
            if (w.length() > keyword.length()) keyword = w;
        }
        return keyword;
    }

    public Mono<String> optimizeBasket(Long userId, java.math.BigDecimal budget) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        var profile = userNutritionProfileRepository.findByUser_Id(userId).orElse(null);
        List<ProductNode> allProducts = productNodeRepository.findAll();

        String context = allProducts.stream()
                .limit(10)
                .map(p -> p.getName() + " (" + p.getPrice() + " VND)")
                .collect(Collectors.joining(", "));

        String prompt = String.format("""
                Tối ưu hóa giỏ hàng cho người dùng SmartGrocery.
                Ngân sách: %.0f VND.
                Hồ sơ sức khỏe: %s.
                Danh sách sản phẩm: %s.
                Hãy đề xuất danh sách sản phẩm tối ưu nhất vừa túi tiền và tốt cho sức khỏe.
                """, budget, profile != null ? profile.getHealthGoals() : "Chưa có dữ liệu", context);

        return geminiAIService.getGeminiResponse(prompt);
    }

    public ChatSession createSession(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return chatSessionRepository.save(ChatSession.builder()
                .user(user)
                .title("Cuá»™c trÃ² chuyá»‡n má»›i")
                .build());
    }
}
