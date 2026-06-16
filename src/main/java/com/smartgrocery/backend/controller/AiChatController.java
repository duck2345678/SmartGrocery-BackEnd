package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.ChatRequestDto;
import com.smartgrocery.backend.dto.ChatResponseDto;
import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.WishlistItemRepository;
import com.smartgrocery.backend.service.ai.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "AI Chat", description = "API nền tảng hội thoại với AI")
public class AiChatController {

    @Autowired private ChatOrchestratorService chatOrchestratorService;
    @Autowired private MealIntentService mealIntentService;
    @Autowired private DiscountIntentService discountIntentService;
    @Autowired private ShoppingItemBuilder shoppingItemBuilder;

    // Fields retained specifically for reflection injection by legacy JUnit tests
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private WishlistItemRepository wishlistItemRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OpenRouterClient openRouterClient;

    public AiChatController() {
    }

    @Operation(summary = "Gửi tin nhắn hội thoại đến AI")
    @PostMapping
    public ResponseEntity<ChatResponseDto> sendMessage(
            @AuthenticationPrincipal User loggedInUser,
            @RequestBody ChatRequestDto requestDto
    ) {
        ChatResponseDto response = chatOrchestratorService.processMessage(loggedInUser, requestDto);
        return ResponseEntity.ok(response);
    }

    // ── LEGACY REFLECTION WRAPPERS FOR JUNIT COMPATIBILITY ──

    private void lazyInitServices() {
        if (shoppingItemBuilder == null) {
            shoppingItemBuilder = new ShoppingItemBuilder(productVariantRepository);
        }
        if (mealIntentService == null) {
            mealIntentService = new MealIntentService(shoppingItemBuilder);
        }
        if (discountIntentService == null) {
            discountIntentService = new DiscountIntentService(
                    productVariantRepository,
                    wishlistItemRepository,
                    openRouterClient,
                    objectMapper,
                    shoppingItemBuilder
            );
        }
    }

    private void syncServices() {
        lazyInitServices();
        try {
            java.lang.reflect.Field builderRepo = ShoppingItemBuilder.class.getDeclaredField("productVariantRepository");
            builderRepo.setAccessible(true);
            builderRepo.set(shoppingItemBuilder, productVariantRepository);

            java.lang.reflect.Field discRepo = DiscountIntentService.class.getDeclaredField("productVariantRepository");
            discRepo.setAccessible(true);
            discRepo.set(discountIntentService, productVariantRepository);

            java.lang.reflect.Field discWish = DiscountIntentService.class.getDeclaredField("wishlistItemRepository");
            discWish.setAccessible(true);
            discWish.set(discountIntentService, wishlistItemRepository);

            java.lang.reflect.Field discMapper = DiscountIntentService.class.getDeclaredField("objectMapper");
            discMapper.setAccessible(true);
            discMapper.set(discountIntentService, objectMapper);
        } catch (Exception e) {
            log.error("[AiChatController] Failed to sync mock repositories for testing", e);
        }
    }

    @SuppressWarnings("unused")
    private Meal findMealByFuzzyName(List<Meal> meals, String targetName) {
        syncServices();
        return mealIntentService.findMealByFuzzyName(meals, targetName);
    }

    @SuppressWarnings("unused")
    private MealSelectionResult detectAndBuildShoppingSelection(
            String userMessage,
            List<Map<String, String>> messages,
            List<Meal> safeMeals,
            Map<Long, List<MealIngredient>> ingredientsByMeal
    ) {
        syncServices();
        MealIntentService.MealSelectionResult result = mealIntentService.detectAndBuildShoppingSelection(
                userMessage, messages, safeMeals, ingredientsByMeal
        );
        return new MealSelectionResult(result.reply(), result.shoppingItems(), result.handled());
    }

    @SuppressWarnings("unused")
    private DiscountIntentResult detectDiscountIntent(String userMessage, Long userId, List<ProductVariant> topDiscountedVariants) {
        syncServices();
        DiscountIntentService.DiscountIntentResult result = discountIntentService.detectDiscountIntent(userMessage, userId);
        return new DiscountIntentResult(result.reply(), result.shoppingItems());
    }

    @SuppressWarnings("unused")
    private String extractDiscountKeyword(String message, boolean normalizedInput) {
        syncServices();
        return discountIntentService.extractDiscountKeyword(message, normalizedInput);
    }

    @SuppressWarnings("unused")
    private Optional<DiscountIntentExtraction> parseDiscountIntentExtraction(String reply) {
        syncServices();
        Optional<DiscountIntentService.DiscountIntentExtraction> opt = discountIntentService.parseDiscountIntentExtraction(reply);
        return opt.map(e -> new DiscountIntentExtraction(e.intent(), e.productName()));
    }

    // Records retained for reflection matching in JUnit tests
    private record MealSelectionResult(String reply, List<ChatResponseDto.ShoppingItem> shoppingItems, boolean handled) {
    }

    private record DiscountIntentResult(String reply, List<ChatResponseDto.ShoppingItem> shoppingItems) {
    }

    private record DiscountIntentExtraction(String intent, String productName) {
    }
}
