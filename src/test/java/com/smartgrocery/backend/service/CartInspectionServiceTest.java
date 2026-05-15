package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.jpa.CartItemRepository;
import com.smartgrocery.backend.repository.jpa.CartRepository;
import com.smartgrocery.backend.repository.jpa.UserNutritionProfileRepository;
import com.smartgrocery.backend.repository.jpa.VariantNutritionFactRepository;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartInspectionServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserNutritionProfileRepository nutritionProfileRepository;

    @Mock
    private VariantNutritionFactRepository nutritionFactRepository;

    @Mock
    private ProductNodeRepository productNodeRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CartInspectionService cartInspectionService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void inspectCart_EmptyCart_ReturnsNoConflicts() {
        // Arrange
        Long userId = 1L;
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // Act
        CartInspectionService.CartInspectionReport report = cartInspectionService.inspectCart(userId);

        // Assert
        assertFalse(report.isHasConflicts());
        assertTrue(report.getWarnings().isEmpty());
        assertTrue(report.getFormattedPromptText().contains("trống"));
    }

    @Test
    void inspectCart_WithAllergenConflict_ReturnsConflicts() {
        // Arrange
        Long userId = 1L;
        Cart cart = new Cart();
        cart.setId(10L);
        
        Product p1 = new Product();
        p1.setId(100L);
        p1.setName("Bánh quy bơ đậu phộng");

        ProductVariant v1 = new ProductVariant();
        v1.setId(1000L);
        v1.setProduct(p1);

        CartItem item = new CartItem();
        item.setVariant(v1);
        item.setQuantity(2);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_Id(cart.getId())).thenReturn(List.of(item));

        // Mock Redis miss
        when(valueOperations.get(anyString())).thenReturn(null);

        // Mock Nutrition
        VariantNutritionFact nf = new VariantNutritionFact();
        nf.setVariant(v1);
        nf.setCaloriesPer100g(BigDecimal.valueOf(200));
        when(nutritionFactRepository.findByProductIds(anyList())).thenReturn(List.of(nf));

        // Mock User Profile
        UserNutritionProfile profile = new UserNutritionProfile();
        profile.setDailyCalorieTarget(2000);
        when(nutritionProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        // Mock Conflict in Neo4j
        ProductNode conflictNode = new ProductNode();
        conflictNode.setProductId(100L);
        conflictNode.setName("Bánh quy bơ đậu phộng");
        when(productNodeRepository.findConflictingProductsForUser(eq(userId), anyList()))
                .thenReturn(List.of(conflictNode));

        // Act
        CartInspectionService.CartInspectionReport report = cartInspectionService.inspectCart(userId);

        // Assert
        assertTrue(report.isHasConflicts());
        assertFalse(report.getWarnings().isEmpty());
        assertTrue(report.getWarnings().stream().anyMatch(w -> w.contains("dị ứng")));
        assertEquals(1, report.getConflictingVariantIds().size());
        assertEquals(1000L, report.getConflictingVariantIds().get(0));
        assertEquals(0, BigDecimal.valueOf(400).compareTo(report.getTotalCalories())); // 2 * 200
        
        verify(valueOperations).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void inspectCart_ExceedsCalories_ReturnsWarning() {
        // Arrange
        Long userId = 2L;
        Cart cart = new Cart();
        cart.setId(20L);
        
        Product p1 = new Product();
        p1.setId(200L);

        ProductVariant v1 = new ProductVariant();
        v1.setId(2000L);
        v1.setProduct(p1);

        CartItem item = new CartItem();
        item.setVariant(v1);
        item.setQuantity(10); // 10 servings

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_Id(cart.getId())).thenReturn(List.of(item));
        when(valueOperations.get(anyString())).thenReturn(null);

        VariantNutritionFact nf = new VariantNutritionFact();
        nf.setVariant(v1);
        nf.setCaloriesPer100g(BigDecimal.valueOf(300)); // 300 * 10 = 3000 kcal
        when(nutritionFactRepository.findByProductIds(anyList())).thenReturn(List.of(nf));

        UserNutritionProfile profile = new UserNutritionProfile();
        profile.setDailyCalorieTarget(2000); // 3000 > 2000 -> Exceeds
        when(nutritionProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));
        when(productNodeRepository.findConflictingProductsForUser(anyLong(), anyList()))
                .thenReturn(List.of()); // No direct allergen conflict

        // Act
        CartInspectionService.CartInspectionReport report = cartInspectionService.inspectCart(userId);

        // Assert
        assertTrue(report.isHasConflicts()); // Exceeding calories flags as conflict
        assertTrue(report.getWarnings().stream().anyMatch(w -> w.contains("vượt quá mục tiêu Calo")));
    }
}
