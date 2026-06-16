package com.smartgrocery.backend.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
public class PromptBuilderService {

    private static final Set<String> GREETING_WORDS = Set.of(
            "chao", "xin chao", "hello", "hi", "hey", "tam biet", "cam on", "thank", "thanks", "bye"
    );

    public String buildSystemPrompt(
            String userMessage,
            String mealCatalog,
            String discountCatalog,
            String oosStr,
            String userProfileStr,
            String customPrompt
    ) {
        if (customPrompt != null && !customPrompt.isBlank()) {
            return customPrompt;
        }

        // Determine if this is a general/generic message to optimize tokens
        boolean genericQuery = isGenericQuery(userMessage);
        log.info("[PromptBuilder] Building prompt. genericQuery={}", genericQuery);

        String meals = genericQuery ? "(Danh sách món ăn có sẵn trong hệ thống, hỏi về món ăn để hiển thị chi tiết)" : mealCatalog;
        String discounts = genericQuery ? "(Danh sách sản phẩm giảm giá hôm nay có sẵn, hỏi về giảm giá để hiển thị chi tiết)" : discountCatalog;
        String oos = genericQuery ? "(Thông tin sản phẩm hết hàng có sẵn)" : oosStr;
        String profile = genericQuery ? "" : userProfileStr;

        return "Bạn là trợ lý mua sắm thông minh của SmartGrocery. Trả lời ngắn gọn, tự nhiên bằng tiếng Việt.\n\n"
                + "=== HƯỚNG DẪN XỬ LÝ YÊU CẦU ===\n\n"
                + "[A] GỢI Ý MÓN ĂN:\n"
                + "- Dùng DANH SÁCH MÓN bên dưới, đánh số 1. 2. 3.\n"
                + "- Khi khách chọn số HOẶC nhắn tên món cụ thể: Xác nhận tên món, nói 'Mình đã chuẩn bị danh sách nguyên liệu cho [Tên] bên dưới rồi nhé! Bạn xem và thêm vào giỏ hàng nha.'. KHÔNG tự liệt kê nguyên liệu trong text.\n"
                + "[B] TÌM SẢN PHẨM hoặc GỢI Ý ẨN Ý:\n"
                + "- Khi khách hỏi mua sản phẩm cụ thể (tương ớt, sữa, nước rửa chén...) HOẶC nói ẩn ý cần mua đồ:\n"
                + "  * 'nhà bẩn quá' → nước lau sàn\n"
                + "  * 'khát nước' → nước suối, nước giải khát\n"
                + "  * 'cần bột giặt' → bột giặt\n"
                + "- Trả lời tự nhiên VÀ BẮT BUỘC thêm tag [SEARCH: <từ_khóa>] Ở CUỐI CÂU (không có khoảng trắng thừa).\n"
                + "- Ví dụ đúng: 'Bạn có thể dùng nước lau sàn Sunlight để vệ sinh sạch sẽ! [SEARCH: nước lau sàn]'\n\n"
                + "[C] CÂU HỎI GIẢM GIÁ:\n"
                + "- Hỏi chung ('có gì giảm', 'sale gì hôm nay'): Trả lời ngắn VÀ thêm [SHOW_DISCOUNTS] ở cuối.\n"
                + "- Hỏi sản phẩm CỤ THỂ có giảm không (vd: 'trứng có giảm giá không'):  Tra DANH SÁCH GIẢM GIÁ.\n"
                + "  * Nếu CÓ → trả lời + [SEARCH: <tên sản phẩm đó>]\n"
                + "  * Nếu KHÔNG → báo không có, hỏi có muốn xem list giảm giá khác không. Nếu khách đồng ý → [SHOW_DISCOUNTS]\n\n"
                + "[D] CÂU HỎI CHUNG (không liên quan mua sắm):\n"
                + "- Trả lời bình thường, tự nhiên, không thêm tag gì cả.\n"
                + "- Sau đó có thể nhẹ nhàng hướng về mua sắm nếu phù hợp.\n\n"
                + "=== DỮ LIỆU ===\n"
                + "DANH SÁCH MÓN:\n" + meals + "\n"
                + "GIẢM GIÁ HÔM NAY (top 10):\n" + discounts + "\n"
                + "HẾT HÀNG: " + oos + "\n"
                + (profile.isBlank() ? "" : "\nHỒ SƠ KHÁCH HÀNG:" + profile);
    }

    private boolean isGenericQuery(String message) {
        if (message == null || message.isBlank()) {
            return true;
        }
        String normalized = normalizeVietnamese(message);
        if (normalized.length() < 3) {
            return true;
        }
        // If it's just a greeting word, it's generic
        if (GREETING_WORDS.contains(normalized)) {
            return true;
        }
        // Check if query is completely conversational/greetings/short chat
        return normalized.matches("^(chao|hi|hello|tam biet|bye|cam on|thanks|thank you|ok|oke|da|roi|yes|no|khong có gì|dung vay)$");
    }

    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        String decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd').replace('\u0110', 'D')
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
