package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.entity.ChatSession;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.service.AIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Assistant", description = "API cho trá»£ lÃ½ mua sáº¯m thÃ´ng minh")
public class AIController {

    @Autowired
    private AIService aiService;

    @Operation(summary = "Nháº­n cÃ¢u tráº£ lá»i tá»« trá»£ lÃ½ AI (cÃ³ kÃ¨m session ID)")
    @GetMapping("/ask")
    public Mono<ResponseEntity<String>> askAI(@RequestParam String query, @RequestParam Long sessionId) {
        return aiService.getAIResponse(query, sessionId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Tạo một phiên chat mới")
    @PostMapping("/session")
    public ResponseEntity<ChatSession> createSession(@RequestParam Long userId) {
        return ResponseEntity.ok(aiService.createSession(userId));
    }

    @Operation(summary = "Tối ưu hóa giỏ hàng dựa trên ngân sách và sức khỏe")
    @GetMapping("/optimize-basket")
    public Mono<ResponseEntity<String>> optimizeBasket(@RequestParam Long userId, @RequestParam java.math.BigDecimal budget) {
        return aiService.optimizeBasket(userId, budget)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Autowired
    private com.smartgrocery.backend.repository.graph.ProductNodeRepository productNodeRepository;

    @Operation(summary = "Tìm kiếm sản phẩm nâng cao bằng Neo4j (Keyword/Ingredient)")
    @GetMapping("/search-advanced")
    public ResponseEntity<java.util.List<com.smartgrocery.backend.entity.graph.ProductNode>> searchAdvanced(
            @RequestParam String query,
            @RequestParam(defaultValue = "keyword") String type) {
        java.util.List<com.smartgrocery.backend.entity.graph.ProductNode> results;
        if ("ingredient".equalsIgnoreCase(type)) {
            results = productNodeRepository.findByIngredient(query);
        } else {
            results = productNodeRepository.searchByKeyword(query);
        }
        return ResponseEntity.ok(results);
    }

    @Operation(summary = "Tìm kiếm sản phẩm liên quan (cùng danh mục) bằng Neo4j")
    @GetMapping("/related-products/{productId}")
    public ResponseEntity<java.util.List<com.smartgrocery.backend.entity.graph.ProductNode>> getRelated(@PathVariable Long productId) {
        return ResponseEntity.ok(productNodeRepository.findRelatedProducts(productId, 5));
    }

    @Operation(summary = "Gợi ý nhắc mua lại sản phẩm theo chu kỳ cá nhân")
    @GetMapping("/nudges")
    public ResponseEntity<java.util.List<com.smartgrocery.backend.dto.AINudgeDto>> getNudges(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(aiService.getNudges(user.getId()));
    }
}
