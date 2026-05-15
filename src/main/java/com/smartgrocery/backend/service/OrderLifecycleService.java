package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.OrderDto;
import com.smartgrocery.backend.dto.OrderItemDto;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.OrderItem;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.AttendanceRecordRepository;
import com.smartgrocery.backend.repository.jpa.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    private static final String STATUS_ASSIGNED = "ASSIGNED";
    private static final String STATUS_PICKING = "PICKING";
    private static final String STATUS_PICKED = "PICKED";
    private static final String STATUS_READY_TO_SHIP = "READY_TO_SHIP";
    private static final String STATUS_DELIVERING = "DELIVERING";
    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final OrderRepository orderRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final Clock clock;

    @Transactional(value = "transactionManager")
    public OrderDto pack(Long orderId, User staff, String packingPhotoUrl) {
        validateStaffCheckedIn(staff);
        Order order = loadAssignableOrder(orderId, staff);
        if (!STATUS_ASSIGNED.equals(order.getStatus()) && !STATUS_PICKING.equals(order.getStatus()) && !STATUS_PICKED.equals(order.getStatus())) {
            throw new IllegalArgumentException("Đơn không ở trạng thái có thể đóng gói (hiện tại: " + order.getStatus() + ")");
        }
        if (packingPhotoUrl == null || packingPhotoUrl.isBlank()) {
            throw new IllegalArgumentException("Vui lòng tải ảnh đóng gói");
        }
        order.setPackingPhotoUrl(packingPhotoUrl.trim());
        order.setPickedAt(LocalDateTime.now(clock));
        order.setStatus(STATUS_READY_TO_SHIP);
        return toDto(orderRepository.save(order));
    }

    @Transactional(value = "transactionManager")
    public OrderDto deliver(Long orderId, User staff, String deliveryPhotoUrl) {
        validateStaffCheckedIn(staff);
        Order order = loadAssignableOrder(orderId, staff);
        if (!STATUS_READY_TO_SHIP.equals(order.getStatus())) {
            throw new IllegalArgumentException("Đơn phải ở trạng thái READY_TO_SHIP mới được bắt đầu giao");
        }
        if (deliveryPhotoUrl == null || deliveryPhotoUrl.isBlank()) {
            throw new IllegalArgumentException("Vui lòng tải ảnh giao hàng");
        }
        order.setDeliveryPhotoUrl(deliveryPhotoUrl.trim());
        order.setStatus(STATUS_DELIVERING);
        return toDto(orderRepository.save(order));
    }

    @Transactional(value = "transactionManager")
    public OrderDto complete(Long orderId, User staff) {
        validateStaffCheckedIn(staff);
        Order order = loadAssignableOrder(orderId, staff);
        if (!STATUS_DELIVERING.equals(order.getStatus())) {
            throw new IllegalArgumentException("Đơn phải ở trạng thái DELIVERING mới được hoàn tất");
        }
        order.setDeliveredAt(LocalDateTime.now(clock));
        order.setStatus(STATUS_DELIVERED);
        return toDto(orderRepository.save(order));
    }

    private void validateStaffCheckedIn(User staff) {
        LocalDate today = LocalDate.now(clock);
        attendanceRecordRepository.findByUser_IdAndWorkDate(staff.getId(), today)
                .stream()
                .filter(r -> r.getCheckInAt() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Bạn chưa vào ca hôm nay. Vui lòng chấm công trước khi xử lý đơn hàng."));
    }

    private Order loadAssignableOrder(Long orderId, User staff) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Đơn hàng không tồn tại"));
        if (order.getAssignee() == null || !order.getAssignee().getId().equals(staff.getId())) {
            throw new IllegalArgumentException("Đơn này không thuộc về bạn");
        }
        if (STATUS_CANCELLED.equals(order.getStatus())) {
            throw new IllegalArgumentException("Đơn đã bị hủy");
        }
        return order;
    }

    private OrderDto toDto(Order order) {
        return OrderDto.builder()
                .id(order.getId())
                .userId(order.getUser() != null ? order.getUser().getId() : null)
                .addressId(order.getAddress() != null ? order.getAddress().getId() : null)
                .orderNumber(order.getOrderNumber())
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .taxAmount(order.getTaxAmount())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .customerNote(order.getCustomerNote())
                .assigneeId(order.getAssignee() != null ? order.getAssignee().getId() : null)
                .leaseExpiresAt(order.getLeaseExpiresAt())
                .packingPhotoUrl(order.getPackingPhotoUrl())
                .deliveryPhotoUrl(order.getDeliveryPhotoUrl())
                .assignedAt(order.getAssignedAt())
                .pickedAt(order.getPickedAt())
                .deliveredAt(order.getDeliveredAt())
                .items(order.getOrderItems() != null ? order.getOrderItems().stream().map(this::mapItem).collect(Collectors.toList()) : null)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderItemDto mapItem(OrderItem item) {
        return OrderItemDto.builder()
                .id(item.getId())
                .variantId(item.getVariant() != null ? item.getVariant().getId() : null)
                .productName(item.getProductName())
                .variantName(item.getVariantName())
                .sku(item.getSku())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .discountAmount(item.getDiscountAmount())
                .totalPrice(item.getTotalPrice())
                .pickedQuantity(item.getPickedQuantity())
                .isSubstituted(item.getIsSubstituted())
                .substitutedVariantId(item.getSubstitutedVariant() != null ? item.getSubstitutedVariant().getId() : null)
                .substitutionReason(item.getSubstitutionReason())
                .build();
    }
}
