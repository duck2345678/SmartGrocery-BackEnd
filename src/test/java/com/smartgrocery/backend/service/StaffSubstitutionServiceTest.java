package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.StaffSubstitutionOptionDto;
import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.OrderItemRepository;
import com.smartgrocery.backend.repository.jpa.OrderRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.WarehouseRepository;
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

import com.smartgrocery.backend.repository.jpa.AttendanceRecordRepository;
import com.smartgrocery.backend.entity.AttendanceRecord;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StaffSubstitutionServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private InventoryStockRepository inventoryStockRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;

    private StaffOrderFlowService newService() {
        StaffOrderFlowService s = new StaffOrderFlowService();
        ReflectionTestUtils.setField(s, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(s, "orderItemRepository", orderItemRepository);
        ReflectionTestUtils.setField(s, "productVariantRepository", productVariantRepository);
        ReflectionTestUtils.setField(s, "inventoryStockRepository", inventoryStockRepository);
        ReflectionTestUtils.setField(s, "warehouseRepository", warehouseRepository);
        ReflectionTestUtils.setField(s, "attendanceRecordRepository", attendanceRecordRepository);
        ReflectionTestUtils.setField(s, "clock", Clock.fixed(Instant.parse("2026-04-19T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        return s;
    }

    private InventoryStockRepository.VariantStockSum mockStock(Long id, Long total) {
        InventoryStockRepository.VariantStockSum m = mock(InventoryStockRepository.VariantStockSum.class);
        when(m.getVariantId()).thenReturn(id);
        when(m.getTotalAvailable()).thenReturn(total);
        return m;
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
        var s1 = mockStock(201L, 10L);
        var s2 = mockStock(202L, 1L);
        var s3 = mockStock(203L, 0L);

        when(inventoryStockRepository.sumAvailableByVariantIds(anyList())).thenReturn(List.of(s1, s2, s3));
        when(attendanceRecordRepository.findByUser_IdAndWorkDateAndCheckInAtIsNotNullAndCheckOutAtIsNull(anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.of(AttendanceRecord.builder().build()));

        List<StaffSubstitutionOptionDto> res = service.getSubstitutions(99L, 1L, staff);
        assertEquals(2, res.size());
        assertEquals(201L, res.get(0).getVariantId());
        assertTrue(Boolean.TRUE.equals(res.get(0).getIsRecommended()));
        assertEquals(202L, res.get(1).getVariantId());
    }
}

