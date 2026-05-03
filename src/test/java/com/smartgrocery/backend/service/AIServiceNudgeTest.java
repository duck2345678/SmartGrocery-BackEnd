package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AINudgeDto;
import com.smartgrocery.backend.entity.Category;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.OrderItem;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AIServiceNudgeTest {

    @Mock
    private OrderRepository orderRepository;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-04-19T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, VN_ZONE);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.ofInstant(FIXED_INSTANT, VN_ZONE);

    private AIService newService() {
        AIService s = new AIService();
        ReflectionTestUtils.setField(s, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(s, "clock", FIXED_CLOCK);
        return s;
    }

    private static Order orderAt(LocalDateTime createdAt, List<OrderItem> items) {
        return Order.builder()
                .id(1L)
                .orderNumber("ORD-1")
                .createdAt(createdAt)
                .orderItems(items)
                .subtotal(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .paymentMethod("COD")
                .build();
    }

    private static OrderItem orderItem(long productId, String productName, BigDecimal unitPriceSnapshot, BigDecimal currentNetPrice, LocalDateTime createdAt) {
        Category cat = Category.builder().id(10L).categoryCode("C").name("Cat").build();
        Product product = Product.builder()
                .id(productId)
                .productCode("P" + productId)
                .name(productName)
                .category(cat)
                .status("ACTIVE")
                .image("/uploads/products/p" + productId + ".jpg")
                .build();
        ProductVariant variant = ProductVariant.builder()
                .id(1000L + productId)
                .product(product)
                .sku("SKU-" + productId)
                .netPrice(currentNetPrice)
                .status("ACTIVE")
                .createdAt(createdAt)
                .build();

        return OrderItem.builder()
                .id(5000L + productId)
                .variant(variant)
                .productName(productName)
                .sku("SKU-" + productId)
                .quantity(1)
                .unitPrice(unitPriceSnapshot)
                .subtotal(unitPriceSnapshot)
                .totalPrice(unitPriceSnapshot)
                .build();
    }

    @Test
    void returnsEmptyForNewUserWithNoOrders() {
        when(orderRepository.findTop200ByUser_IdAndStatusNotOrderByCreatedAtDesc(1L, "CANCELLED"))
                .thenReturn(List.of());

        AIService service = newService();
        List<AINudgeDto> nudges = service.getNudges(1L);

        assertNotNull(nudges);
        assertTrue(nudges.isEmpty());
    }

    @Test
    void doesNotCrashWhenOrderHasNullItems() {
        when(orderRepository.findTop200ByUser_IdAndStatusNotOrderByCreatedAtDesc(1L, "CANCELLED"))
                .thenReturn(List.of(orderAt(FIXED_NOW.minusDays(10), null)));

        AIService service = newService();
        List<AINudgeDto> nudges = service.getNudges(1L);

        assertNotNull(nudges);
        assertTrue(nudges.isEmpty());
    }

    @Test
    void singlePurchaseDueReturnsNudgeWithNonZeroConfidence() {
        LocalDateTime now = FIXED_NOW;
        OrderItem eggs = orderItem(101L, "Trứng gà", BigDecimal.valueOf(30000), BigDecimal.valueOf(35000), now.minusDays(20));
        when(orderRepository.findTop200ByUser_IdAndStatusNotOrderByCreatedAtDesc(1L, "CANCELLED"))
                .thenReturn(List.of(orderAt(now.minusDays(20), List.of(eggs))));

        AIService service = newService();
        List<AINudgeDto> nudges = service.getNudges(1L);

        assertEquals(1, nudges.size());
        AINudgeDto dto = nudges.get(0);
        assertEquals(101L, dto.getProductId());
        assertNotNull(dto.getReason());
        assertTrue(dto.getReason().toLowerCase().contains("đã"));
        assertNotNull(dto.getConfidenceScore());
        assertTrue(dto.getConfidenceScore() >= 0.5);
        assertEquals(BigDecimal.valueOf(35000), dto.getPrice());
        assertEquals("/uploads/products/p101.jpg", dto.getImage());
    }

    @Test
    void loyalCadenceProducesHighConfidenceAndUsesCurrentPriceNotSnapshot() {
        LocalDateTime now = FIXED_NOW;
        OrderItem milk1 = orderItem(205L, "Sữa tươi", BigDecimal.valueOf(25000), BigDecimal.valueOf(32000), now.minusDays(22));
        OrderItem milk2 = orderItem(205L, "Sữa tươi", BigDecimal.valueOf(26000), BigDecimal.valueOf(32000), now.minusDays(15));
        OrderItem milk3 = orderItem(205L, "Sữa tươi", BigDecimal.valueOf(27000), BigDecimal.valueOf(32000), now.minusDays(8));

        when(orderRepository.findTop200ByUser_IdAndStatusNotOrderByCreatedAtDesc(1L, "CANCELLED"))
                .thenReturn(List.of(
                        orderAt(now.minusDays(8), List.of(milk3)),
                        orderAt(now.minusDays(15), List.of(milk2)),
                        orderAt(now.minusDays(22), List.of(milk1))
                ));

        AIService service = newService();
        List<AINudgeDto> nudges = service.getNudges(1L);

        assertEquals(1, nudges.size());
        AINudgeDto dto = nudges.get(0);
        assertEquals(205L, dto.getProductId());
        assertNotNull(dto.getReason());
        assertTrue(dto.getReason().contains("mỗi"));
        assertNotNull(dto.getConfidenceScore());
        assertTrue(dto.getConfidenceScore() > 0.8);
        assertEquals(BigDecimal.valueOf(32000), dto.getPrice());
    }

    @Test
    void excludesCancelledByRepositoryContract() {
        when(orderRepository.findTop200ByUser_IdAndStatusNotOrderByCreatedAtDesc(anyLong(), anyString()))
                .thenReturn(List.of());

        AIService service = newService();
        service.getNudges(99L);

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderRepository, times(1))
                .findTop200ByUser_IdAndStatusNotOrderByCreatedAtDesc(userIdCaptor.capture(), statusCaptor.capture());

        assertEquals(99L, userIdCaptor.getValue());
        assertEquals("CANCELLED", statusCaptor.getValue());
    }
}
