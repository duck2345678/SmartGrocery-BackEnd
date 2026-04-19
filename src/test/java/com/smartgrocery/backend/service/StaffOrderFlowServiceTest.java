package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AssignOrderResponse;
import com.smartgrocery.backend.dto.CompletePickingRequest;
import com.smartgrocery.backend.entity.InventoryStock;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.OrderItem;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.Warehouse;
import com.smartgrocery.backend.exception.OrderAssignmentConflictException;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StaffOrderFlowServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private InventoryStockRepository inventoryStockRepository;
    @Mock
    private WarehouseRepository warehouseRepository;

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
    void assignOrderReturns409WhenAlreadyAssigned() {
        StaffOrderFlowService service = newService();
        User staff = User.builder().id(5L).build();

        when(orderRepository.assignIfAvailable(anyLong(), anyLong(), any(), anyString(), anyString(), any()))
                .thenReturn(0);

        assertThrows(OrderAssignmentConflictException.class, () -> service.assignOrder(100L, staff));
    }

    @Test
    void assignOrderSuccessReturnsLease() {
        StaffOrderFlowService service = newService();
        User staff = User.builder().id(5L).build();

        when(orderRepository.assignIfAvailable(eq(100L), eq(5L), any(), eq("ASSIGNED"), eq("PENDING"), any()))
                .thenReturn(1);

        AssignOrderResponse res = service.assignOrder(100L, staff);
        assertEquals(100L, res.getOrderId());
        assertEquals(5L, res.getAssigneeId());
        assertEquals("ASSIGNED", res.getStatus());
        assertNotNull(res.getLeaseExpiresAt());
    }

    @Test
    void heartbeatFailsWhenLeaseExpiredOrNotOwner() {
        StaffOrderFlowService service = newService();
        User staff = User.builder().id(7L).build();

        when(orderRepository.heartbeatLease(anyLong(), anyLong(), any(), anyList(), any()))
                .thenReturn(0);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.heartbeat(99L, staff));
        assertTrue(ex.getMessage().toLowerCase().contains("unauthorized"));
    }

    @Test
    void heartbeatSuccessReturnsUpdatedLease() {
        StaffOrderFlowService service = newService();
        User staff = User.builder().id(7L).build();

        when(orderRepository.heartbeatLease(anyLong(), anyLong(), any(), anyList(), any()))
                .thenReturn(1);
        when(orderRepository.findById(99L)).thenReturn(Optional.of(Order.builder().id(99L).status("ASSIGNED").build()));

        AssignOrderResponse res = service.heartbeat(99L, staff);
        assertEquals(99L, res.getOrderId());
        assertEquals("ASSIGNED", res.getStatus());
        assertNotNull(res.getLeaseExpiresAt());
    }

    @Test
    void releaseSuccessReturnsPending() {
        StaffOrderFlowService service = newService();
        User staff = User.builder().id(7L).build();

        when(orderRepository.releaseAssignment(eq(99L), eq(7L), eq("PENDING"), anyList()))
                .thenReturn(1);

        AssignOrderResponse res = service.release(99L, staff);
        assertEquals("PENDING", res.getStatus());
        assertNull(res.getAssigneeId());
    }

    @Test
    void getQueueUsesPendingAndNow() {
        StaffOrderFlowService service = newService();
        when(orderRepository.findQueueForAssignment(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(List.of());
        assertNotNull(service.getQueue());
        verify(orderRepository, times(1)).findQueueForAssignment(eq("PENDING"), any(LocalDateTime.class));
    }

    @Test
    void completePickingRejectsMoreExpensiveSubstitution() {
        StaffOrderFlowService service = newService();
        User staff = User.builder().id(7L).build();

        ProductVariant original = ProductVariant.builder().id(101L).netPrice(java.math.BigDecimal.valueOf(30000)).build();
        OrderItem oi = OrderItem.builder()
                .id(1L)
                .variant(original)
                .quantity(1)
                .unitPrice(java.math.BigDecimal.valueOf(30000))
                .allowSubstitution(true)
                .build();
        Order order = Order.builder()
                .id(99L)
                .status("ASSIGNED")
                .assignee(staff)
                .leaseExpiresAt(LocalDateTime.of(2026, 4, 19, 18, 0))
                .shippingFee(java.math.BigDecimal.ZERO)
                .orderItems(new ArrayList<>(List.of(oi)))
                .build();
        oi.setOrder(order);

        ProductVariant expensiveSub = ProductVariant.builder().id(202L).netPrice(java.math.BigDecimal.valueOf(35000)).build();

        when(orderRepository.findById(99L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrder_Id(99L)).thenReturn(List.of(oi));
        when(productVariantRepository.findById(202L)).thenReturn(Optional.of(expensiveSub));
        when(warehouseRepository.findAll()).thenReturn(List.of(Warehouse.builder().id(1L).build()));

        CompletePickingRequest req = CompletePickingRequest.builder()
                .pickedItems(List.of(CompletePickingRequest.PickedItem.builder()
                        .originalOrderItemId(1L)
                        .actualQuantity(1)
                        .isSubstituted(true)
                        .substitutedVariantId(202L)
                        .reason("test")
                        .build()))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.completePicking(99L, staff, req));
        assertTrue(ex.getMessage().toLowerCase().contains("giá thay thế"));
    }
}
