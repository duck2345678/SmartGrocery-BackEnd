package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.StaffSubstitutionOptionDto;
import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.repository.InventoryStockRepository;
import com.smartgrocery.backend.repository.OrderItemRepository;
import com.smartgrocery.backend.repository.OrderRepository;
import com.smartgrocery.backend.repository.ProductVariantRepository;
import com.smartgrocery.backend.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StaffSubstitutionServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private InventoryStockRepository inventoryStockRepository;
    @Mock private WarehouseRepository warehouseRepository;

    private StaffOrderFlowService newService() {
        StaffOrderFlowService s = new StaffOrderFlowService();
        ReflectionTestUtils.setField(s, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(s, "orderItemRepository", orderItemRepository);
        ReflectionTestUtils.setField(s, "productVariantRepository", productVariantRepository);
        ReflectionTestUtils.setField(s, "inventoryStockRepository", inventoryStockRepository);
        ReflectionTestUtils.setField(s, "warehouseRepository", warehouseRepository);
        ReflectionTestUtils.setField(s, "clock", Clock.fixed(Instant.parse("2026-04-19T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        return s;
    }

    @Test
    void returnsOnlyStockPositiveAndPriceNotGreaterThanOriginal() {
        StaffOrderFlowService service = newService();

        User staff = User.builder().id(7L).build();
        Order order = Order.builder()
                .id(99L)
                .status("ASSIGNED")
                .assignee(staff)
                .leaseExpiresAt(LocalDateTime.of(2026, 4, 19, 18, 0))
                .build();

        Category cat = Category.builder().id(2L).categoryCode("C").name("Cat").build();
        Product p = Product.builder().id(1L).productCode("P1").name("Milk").category(cat).status("ACTIVE").build();

        ProductVariant original = ProductVariant.builder().id(101L).product(p).netPrice(BigDecimal.valueOf(30000)).status("ACTIVE").build();
        OrderItem oi = OrderItem.builder().id(1L).order(order).variant(original).unitPrice(BigDecimal.valueOf(30000)).build();

        ProductVariant ok1 = ProductVariant.builder().id(201L).product(p).variantName("OK").netPrice(BigDecimal.valueOf(30000)).status("ACTIVE").build();
        ProductVariant ok2 = ProductVariant.builder().id(202L).product(p).variantName("OK2").netPrice(BigDecimal.valueOf(25000)).status("ACTIVE").build();
        ProductVariant oos = ProductVariant.builder().id(203L).product(p).variantName("OOS").netPrice(BigDecimal.valueOf(20000)).status("ACTIVE").build();

        when(orderRepository.findById(99L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(oi));
        when(productVariantRepository.findTop50ByProduct_Category_IdAndStatusAndNetPriceLessThanEqualOrderByNetPriceDesc(2L, "ACTIVE", BigDecimal.valueOf(30000)))
                .thenReturn(List.of(original, ok1, ok2, oos));
        when(inventoryStockRepository.sumAvailableByVariantId(201L)).thenReturn(10L);
        when(inventoryStockRepository.sumAvailableByVariantId(202L)).thenReturn(1L);
        when(inventoryStockRepository.sumAvailableByVariantId(203L)).thenReturn(0L);

        List<StaffSubstitutionOptionDto> res = service.getSubstitutions(99L, 1L, staff);
        assertEquals(2, res.size());
        assertEquals(201L, res.get(0).getVariantId());
        assertTrue(Boolean.TRUE.equals(res.get(0).getIsRecommended()));
        assertEquals(202L, res.get(1).getVariantId());
    }
}

