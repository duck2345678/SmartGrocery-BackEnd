package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AINudgeDto;
import com.smartgrocery.backend.entity.ChatMessage;
import com.smartgrocery.backend.entity.ChatSession;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.OrderItem;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.ChatMessageRepository;
import com.smartgrocery.backend.repository.ChatSessionRepository;
import com.smartgrocery.backend.repository.OrderRepository;
import com.smartgrocery.backend.repository.UserRepository;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private OrderRepository orderRepository;

    @Autowired
    private com.smartgrocery.backend.repository.UserNutritionProfileRepository userNutritionProfileRepository;

    @Autowired
    private Clock clock;

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

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<AINudgeDto> getNudges(Long userId) {
        if (userId == null) return List.of();
        List<Order> orders = orderRepository.findTop200ByUser_IdAndStatusNotOrderByCreatedAtDesc(userId, "CANCELLED");
        if (orders.isEmpty()) return List.of();

        class ProductHistory {
            Long productId;
            String name;
            String image;
            java.math.BigDecimal price;
            List<LocalDate> dates = new ArrayList<>();
        }

        Map<Long, ProductHistory> byProduct = new HashMap<>();
        for (Order order : orders) {
            LocalDate orderDate = order.getCreatedAt() != null ? order.getCreatedAt().toLocalDate() : LocalDate.now(clock);
            List<OrderItem> items = order.getOrderItems() != null ? order.getOrderItems() : List.of();
            for (OrderItem item : items) {
                if (item.getVariant() == null || item.getVariant().getProduct() == null) continue;
                Long productId = item.getVariant().getProduct().getId();
                if (productId == null) continue;
                ProductHistory h = byProduct.computeIfAbsent(productId, __ -> {
                    ProductHistory x = new ProductHistory();
                    x.productId = productId;
                    x.name = item.getProductName();
                    x.image = item.getVariant().getProduct().getImage();
                    x.price = item.getVariant().getNetPrice();
                    return x;
                });
                h.dates.add(orderDate);
                if (h.price == null) h.price = item.getVariant().getNetPrice();
                if (h.image == null) h.image = item.getVariant().getProduct().getImage();
                if (h.name == null) h.name = item.getProductName();
            }
        }

        LocalDate now = LocalDate.now(clock);
        List<AINudgeDto> candidates = new ArrayList<>();
        for (ProductHistory h : byProduct.values()) {
            if (h.dates.isEmpty()) continue;
            h.dates.sort(LocalDate::compareTo);
            LocalDate last = h.dates.get(h.dates.size() - 1);
            long daysSinceLast = ChronoUnit.DAYS.between(last, now);

            int cadence;
            if (h.dates.size() >= 2) {
                long total = 0;
                for (int i = 1; i < h.dates.size(); i++) {
                    total += Math.max(1, ChronoUnit.DAYS.between(h.dates.get(i - 1), h.dates.get(i)));
                }
                cadence = Math.max(3, (int) Math.round((double) total / (h.dates.size() - 1)));
            } else {
                cadence = 14;
            }

            boolean due = daysSinceLast >= Math.max(3, Math.round(cadence * 0.8f));
            if (!due) continue;

            double confidence = Math.min(0.95, 0.45 + Math.min(0.35, h.dates.size() * 0.07) + Math.min(0.2, (double) daysSinceLast / Math.max(1, cadence * 2)));
            String reason;
            if (h.dates.size() >= 2) {
                reason = String.format(Locale.ROOT, "Thường bạn mua món này mỗi %d ngày", cadence);
            } else {
                reason = String.format(Locale.ROOT, "Đã %d ngày bạn chưa mua lại món này", daysSinceLast);
            }

            candidates.add(AINudgeDto.builder()
                    .productId(h.productId)
                    .name(h.name)
                    .image(h.image)
                    .price(h.price)
                    .reason(reason)
                    .confidenceScore(Math.round(confidence * 100.0) / 100.0)
                    .build());
        }

        candidates.sort(Comparator.comparing(AINudgeDto::getConfidenceScore, Comparator.nullsLast(Comparator.reverseOrder())));
        return candidates.stream().limit(5).collect(Collectors.toList());
    }
}
