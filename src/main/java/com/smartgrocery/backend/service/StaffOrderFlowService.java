package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AssignOrderResponse;
import com.smartgrocery.backend.dto.CompletePickingRequest;
import com.smartgrocery.backend.dto.StaffPickItemDto;
import com.smartgrocery.backend.dto.StaffPickOrderDto;
import com.smartgrocery.backend.dto.StaffOrderDto;
import com.smartgrocery.backend.dto.StaffPerformanceDailyDto;
import com.smartgrocery.backend.dto.StaffPerformanceOrderDto;
import com.smartgrocery.backend.dto.StaffPerformanceSummaryDto;
import com.smartgrocery.backend.dto.StaffSubstitutionOptionDto;
import com.smartgrocery.backend.entity.InventoryStock;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.OrderItem;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.Warehouse;
import com.smartgrocery.backend.exception.OrderAssignmentConflictException;
import com.smartgrocery.backend.repository.AttendanceRecordRepository;
import com.smartgrocery.backend.repository.InventoryStockRepository;
import com.smartgrocery.backend.repository.OrderItemRepository;
import com.smartgrocery.backend.repository.OrderRepository;
import com.smartgrocery.backend.repository.ProductVariantRepository;
import com.smartgrocery.backend.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.time.temporal.TemporalAdjusters;
import java.util.stream.Collectors;

@Service
public class StaffOrderFlowService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ASSIGNED = "ASSIGNED";
    private static final String STATUS_PICKING = "PICKING";
    private static final String STATUS_PICKED = "PICKED";
    private static final String STATUS_READY_TO_SHIP = "READY_TO_SHIP";
    private static final String STATUS_DELIVERING = "DELIVERING";
        private static final int LEASE_MINUTES = 30;

    @Autowired
    private OrderRepository orderRepository;

        @Autowired
        private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private Clock clock;

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<StaffOrderDto> getQueue(User staffUser) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<StaffOrderDto> activeOrders = orderRepository.findActiveLeaseOrdersByStaff(
                staffUser.getId(),
                List.of(STATUS_ASSIGNED, STATUS_PICKING, STATUS_PICKED, STATUS_READY_TO_SHIP, STATUS_DELIVERING),
                now
        ).stream()
                .map(this::toStaffOrderDto)
                .collect(Collectors.toList());
        if (!activeOrders.isEmpty()) {
            return List.of();
        }
        return orderRepository.findPersonalQueueForAssignment(staffUser.getId(), "QUEUED").stream()
                .limit(1)
                .map(this::toStaffOrderDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Optional<StaffOrderDto> getMyActiveOrder(User staffUser) {
        LocalDateTime now = LocalDateTime.now(clock);
        return orderRepository.findActiveLeaseOrdersByStaff(
                        staffUser.getId(),
                        List.of(STATUS_ASSIGNED, STATUS_PICKING, STATUS_PICKED, STATUS_READY_TO_SHIP, STATUS_DELIVERING),
                        now
                ).stream()
                .findFirst()
                .map(this::toStaffOrderDto);
    }

    @Transactional(value = "transactionManager")
    public AssignOrderResponse assignOrder(Long orderId, User staffUser) {
        LocalDateTime now = LocalDateTime.now(clock);
        ensureStaffInShift(staffUser);
        long activeCount = orderRepository.countActiveAssignments(
                staffUser.getId(),
                List.of(STATUS_ASSIGNED, STATUS_PICKING, STATUS_PICKED, STATUS_READY_TO_SHIP, STATUS_DELIVERING),
                now
        );
        if (activeCount > 0) {
            throw new OrderAssignmentConflictException("Bạn đang xử lý một đơn hàng. Vui lòng hoàn thành đơn hiện tại trước khi nhận đơn mới.");
        }
        LocalDateTime leaseExpiresAt = now.plusMinutes(LEASE_MINUTES);
        int updated = orderRepository.acceptQueuedOrder(
                orderId,
                staffUser.getId(),
                leaseExpiresAt,
                STATUS_ASSIGNED,
                "QUEUED",
                now
        );
        if (updated == 0) {
            throw new OrderAssignmentConflictException("Order is not in your queue or no longer available");
        }
        return AssignOrderResponse.builder()
                .orderId(orderId)
                .assigneeId(staffUser.getId())
                .status(STATUS_ASSIGNED)
                .leaseExpiresAt(leaseExpiresAt)
                .build();
    }

    @Transactional(value = "transactionManager")
    public AssignOrderResponse heartbeat(Long orderId, User staffUser) {
        LocalDateTime now = LocalDateTime.now(clock);
                ensureStaffInShift(staffUser);
        LocalDateTime leaseExpiresAt = now.plusMinutes(LEASE_MINUTES);
        int updated = orderRepository.heartbeatLease(
                orderId,
                staffUser.getId(),
                leaseExpiresAt,
                List.of(STATUS_ASSIGNED, STATUS_PICKING),
                now
        );
        if (updated == 0) {
            throw new IllegalArgumentException("Không thể gia hạn: đơn hàng không hợp lệ hoặc đã hết hạn (invalid heartbeat)");
        }
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        return AssignOrderResponse.builder()
                .orderId(orderId)
                .assigneeId(staffUser.getId())
                .status(order.getStatus())
                .leaseExpiresAt(leaseExpiresAt)
                .build();
    }

    @Transactional(value = "transactionManager")
    public AssignOrderResponse release(Long orderId, User staffUser) {
                ensureStaffInShift(staffUser);
        int updated = orderRepository.releaseAssignment(
                orderId,
                staffUser.getId(),
                STATUS_PENDING,
                List.of(STATUS_ASSIGNED, STATUS_PICKING)
        );
        if (updated == 0) {
            throw new IllegalArgumentException("Không thể bỏ qua: đơn hàng không hợp lệ (invalid release)");
        }
        return AssignOrderResponse.builder()
                .orderId(orderId)
                .assigneeId(null)
                .status(STATUS_PENDING)
                .leaseExpiresAt(null)
                .build();
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public StaffPickOrderDto getPickList(Long orderId, User staffUser) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
                ensureStaffInShift(staffUser);
        ensureValidLeaseOwner(order, staffUser);
        List<OrderItem> items = orderItemRepository.findByOrder_IdOrderByVariant_AisleLocationAsc(orderId);
        String addressLine = order.getAddress() != null
                ? order.getAddress().getStreetAddress() + ", " + order.getAddress().getWard() + ", " + order.getAddress().getDistrict()
                : null;
        return StaffPickOrderDto.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .assigneeId(order.getAssignee() != null ? order.getAssignee().getId() : null)
                .leaseExpiresAt(order.getLeaseExpiresAt())
                .packingPhotoUrl(order.getPackingPhotoUrl())
                .deliveryPhotoUrl(order.getDeliveryPhotoUrl())
                .items(items.stream().map(this::toPickItemDto).collect(Collectors.toList()))
                .customerName(order.getUser() != null ? order.getUser().getFullName() : null)
                .customerPhone(order.getUser() != null ? order.getUser().getPhone() : null)
                .customerEmail(order.getUser() != null ? order.getUser().getEmail() : null)
                .addressLine(addressLine)
                .paymentMethod(order.getPaymentMethod())
                .subtotal(order.getSubtotal())
                .totalAmount(order.getTotalAmount())
                .orderDate(order.getCreatedAt())
                .deliveryDate(order.getDeliveredAt())
                .build();
    }

    @Transactional(value = "transactionManager")
    public StaffPickOrderDto completePicking(Long orderId, User staffUser, CompletePickingRequest request) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
                ensureStaffInShift(staffUser);
        ensureValidLeaseOwner(order, staffUser);
        if (request == null || request.getPickedItems() == null || request.getPickedItems().isEmpty()) {
            throw new IllegalArgumentException("Thiếu pickedItems");
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrder_Id(orderId);
        Map<Long, OrderItem> byId = orderItems.stream().collect(Collectors.toMap(OrderItem::getId, x -> x));
        Set<Long> reqIds = request.getPickedItems().stream().map(CompletePickingRequest.PickedItem::getOriginalOrderItemId).collect(Collectors.toSet());
        if (reqIds.size() != orderItems.size() || !byId.keySet().equals(reqIds)) {
            throw new IllegalArgumentException("pickedItems phải chứa đầy đủ tất cả order items");
        }

        Warehouse warehouse = warehouseRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho hàng"));

        BigDecimal newSubtotal = BigDecimal.ZERO;

        for (CompletePickingRequest.PickedItem picked : request.getPickedItems()) {
            OrderItem item = byId.get(picked.getOriginalOrderItemId());
            if (item == null) throw new IllegalArgumentException("Order item không tồn tại");

            int orderedQty = item.getQuantity() != null ? item.getQuantity() : 0;
            int actualQty = picked.getActualQuantity() != null ? picked.getActualQuantity() : 0;
            if (actualQty < 0 || actualQty > orderedQty) {
                throw new IllegalArgumentException("actualQuantity không hợp lệ cho orderItem=" + item.getId());
            }

            boolean substituted = Boolean.TRUE.equals(picked.getIsSubstituted());
            if (substituted) {
                throw new IllegalArgumentException("Thay thế tương đương đã bị vô hiệu hóa hệ thống");
            } else {
                // Keep original item, return any unpicked quantity back to stock
                int returnedQty = orderedQty - actualQty;
                if (returnedQty > 0) {
                    InventoryStock stockOriginal = inventoryStockRepository.findByWarehouseIdAndVariantId(warehouse.getId(), item.getVariant().getId())
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy tồn kho món gốc"));
                    stockOriginal.setAvailableQuantity((stockOriginal.getAvailableQuantity() != null ? stockOriginal.getAvailableQuantity() : 0) + returnedQty);
                    inventoryStockRepository.save(stockOriginal);
                }
                item.setIsSubstituted(false);
                item.setSubstitutedVariant(null);
                item.setSubstitutionReason(picked.getReason());
                item.setPickedQuantity(actualQty);
                BigDecimal line = (item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO).multiply(BigDecimal.valueOf(actualQty));
                item.setSubtotal(line);
                item.setTotalPrice(line);
                orderItemRepository.save(item);
                newSubtotal = newSubtotal.add(line);
            }
        }

        order.setSubtotal(newSubtotal);
        order.setTotalAmount(newSubtotal.add(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO));
        order.setStatus(STATUS_PICKED);
        order.setPickedAt(LocalDateTime.now(clock));
        order.setLeaseExpiresAt(null);
        orderRepository.save(order);

        return getPickList(orderId, staffUser);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public StaffPerformanceDailyDto getPerformanceDaily(User staffUser, LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now(clock);
        LocalDateTime from = effectiveDate.atStartOfDay();
        LocalDateTime to = effectiveDate.plusDays(1).atStartOfDay();

        List<String> completedStatuses = List.of("DELIVERED");
        long completedCount = orderRepository.countCompletedOrdersByStaffAndPickedAtRange(
                staffUser.getId(),
                completedStatuses,
                from,
                to
        );

        List<StaffPerformanceOrderDto> orders = orderRepository.findCompletedOrdersByStaffAndPickedAtRange(
                        staffUser.getId(),
                        completedStatuses,
                        from,
                        to
                ).stream()
                .limit(200)
                .map(o -> StaffPerformanceOrderDto.builder()
                        .orderId(o.getId())
                        .orderNumber(o.getOrderNumber())
                        .completedAt(o.getUpdatedAt())
                        .status(o.getStatus())
                        .build())
                .collect(Collectors.toList());

        return StaffPerformanceDailyDto.builder()
                .date(effectiveDate)
                .completedCount(completedCount)
                .orders(orders)
                .build();
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public StaffPerformanceSummaryDto getPerformanceSummary(User staffUser, LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now(clock);

        LocalDate weekFrom = effectiveDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekTo = weekFrom.plusDays(6);
        LocalDate monthFrom = effectiveDate.withDayOfMonth(1);
        LocalDate monthTo = monthFrom.plusMonths(1).minusDays(1);

        long weekCompletedCount = orderRepository.countCompletedOrdersByStaffAndPickedAtRange(
                staffUser.getId(),
                List.of("DELIVERED"),
                weekFrom.atStartOfDay(),
                weekTo.plusDays(1).atStartOfDay()
        );

        long monthCompletedCount = orderRepository.countCompletedOrdersByStaffAndPickedAtRange(
                staffUser.getId(),
                List.of("DELIVERED"),
                monthFrom.atStartOfDay(),
                monthTo.plusDays(1).atStartOfDay()
        );

        return StaffPerformanceSummaryDto.builder()
                .date(effectiveDate)
                .weekFrom(weekFrom)
                .weekTo(weekTo)
                .weekCompletedCount(weekCompletedCount)
                .monthFrom(monthFrom)
                .monthTo(monthTo)
                .monthCompletedCount(monthCompletedCount)
                .build();
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<StaffSubstitutionOptionDto> getSubstitutions(Long orderId, Long orderItemId, User staffUser) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
                ensureStaffInShift(staffUser);
        ensureValidLeaseOwner(order, staffUser);

        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new RuntimeException("Order item not found"));
        if (orderItem.getOrder() == null || !Objects.equals(orderItem.getOrder().getId(), orderId)) {
            throw new RuntimeException("Order item not in order");
        }

        BigDecimal originalUnitPrice = orderItem.getUnitPrice() != null ? orderItem.getUnitPrice() : BigDecimal.ZERO;
        Long categoryId = orderItem.getVariant() != null
                && orderItem.getVariant().getProduct() != null
                && orderItem.getVariant().getProduct().getCategory() != null
                ? orderItem.getVariant().getProduct().getCategory().getId()
                : null;
        if (categoryId == null) return List.of();

        Long originalVariantId = orderItem.getVariant() != null ? orderItem.getVariant().getId() : null;
        List<ProductVariant> candidates = productVariantRepository
                .findTop50ByProduct_Category_IdAndStatusAndNetPriceLessThanEqualOrderByNetPriceDesc(categoryId, "ACTIVE", originalUnitPrice);

        List<Long> candidateIds = candidates.stream()
                .map(ProductVariant::getId)
                .filter(Objects::nonNull)
                .filter(id -> originalVariantId == null || !Objects.equals(id, originalVariantId))
                .toList();

        Map<Long, Integer> stockByVariantId = candidateIds.isEmpty()
                ? Map.of()
                : inventoryStockRepository.sumAvailableByVariantIds(candidateIds).stream()
                .collect(Collectors.toMap(
                        InventoryStockRepository.VariantStockSum::getVariantId,
                        x -> x.getTotalAvailable() != null ? x.getTotalAvailable().intValue() : 0
                ));

        List<StaffSubstitutionOptionDto> options = candidates.stream()
                .filter(v -> originalVariantId == null || !Objects.equals(v.getId(), originalVariantId))
                .map(v -> {
                    int stock = stockByVariantId.getOrDefault(v.getId(), 0);
                    String name = v.getProduct() != null
                            ? (v.getProduct().getName() + (v.getVariantName() != null ? " • " + v.getVariantName() : ""))
                            : (v.getVariantName() != null ? v.getVariantName() : ("Variant " + v.getId()));
                    return StaffSubstitutionOptionDto.builder()
                            .variantId(v.getId())
                            .name(name)
                            .price(v.getNetPrice())
                            .stock(stock)
                            .isRecommended(false)
                            .build();
                })
                .filter(o -> o.getStock() != null && o.getStock() > 0)
                .sorted(Comparator
                        .comparing(StaffSubstitutionOptionDto::getPrice, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StaffSubstitutionOptionDto::getStock, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .collect(Collectors.toList());

        if (!options.isEmpty()) {
            options.get(0).setIsRecommended(true);
        }

        return options;
    }

    /* Post-picking states: lease is no longer relevant once picking is done */
    private static final Set<String> POST_PICKING_STATUSES = Set.of(
            STATUS_PICKED, STATUS_READY_TO_SHIP, STATUS_DELIVERING, "DELIVERED"
    );

    private void ensureValidLeaseOwner(Order order, User staffUser) {
        // 1. Must have an assignee
        if (order.getAssignee() == null || order.getAssignee().getId() == null) {
            throw new IllegalArgumentException("Đơn hàng chưa được nhận xử lý.");
        }
        // 2. Must be assigned to THIS staff
        if (!order.getAssignee().getId().equals(staffUser.getId())) {
            throw new IllegalArgumentException("Đơn hàng đã được nhận bởi nhân viên khác.");
        }
        // 3. Skip lease check for post-picking states (lease was cleared by completePicking)
        if (POST_PICKING_STATUSES.contains(order.getStatus())) {
            return;
        }
        // 4. During picking phase: lease must be valid
        LocalDateTime now = LocalDateTime.now(clock);
        if (order.getLeaseExpiresAt() == null || order.getLeaseExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Thời gian xử lý đơn hàng đã hết hạn. Vui lòng nhận lại đơn.");
        }
    }

        private void ensureStaffInShift(User staffUser) {
                LocalDate today = LocalDate.now(clock);
                boolean activeShift = attendanceRecordRepository
                                .findByUser_IdAndWorkDateAndCheckInAtIsNotNullAndCheckOutAtIsNull(staffUser.getId(), today)
                                .isPresent();
                if (!activeShift) {
                        throw new OrderAssignmentConflictException("Nhân viên phải đang trong ca làm việc để xử lý đơn hàng.");
                }
        }

    private StaffPickItemDto toPickItemDto(OrderItem item) {
        return StaffPickItemDto.builder()
                .orderItemId(item.getId())
                .variantId(item.getVariant().getId())
                .sku(item.getSku())
                .barcode(item.getVariant() != null ? item.getVariant().getBarcode() : null)
                .productName(item.getProductName())
                .variantName(item.getVariantName())
                .aisleLocation(item.getVariant().getAisleLocation())
                .orderedQuantity(item.getQuantity())
                .pickedQuantity(item.getPickedQuantity())
                .unitPrice(item.getUnitPrice())
                .imageUrl(item.getVariant() != null && item.getVariant().getProduct() != null ? item.getVariant().getProduct().getImage() : null)
                .build();
    }

    private StaffOrderDto toStaffOrderDto(Order o) {
        int totalItems = o.getOrderItems() != null
                ? o.getOrderItems().stream().mapToInt(OrderItem::getQuantity).sum()
                : 0;
        String addressLine = o.getAddress() != null
                ? o.getAddress().getStreetAddress() + ", " + o.getAddress().getWard() + ", " + o.getAddress().getDistrict()
                : null;
        return StaffOrderDto.builder()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .status(o.getStatus())
                .customerId(o.getUser() != null ? o.getUser().getId() : null)
                .customerName(o.getUser() != null ? o.getUser().getFullName() : null)
                .customerPhone(o.getUser() != null ? o.getUser().getPhone() : null)
                .addressLine(addressLine)
                .totalItems(totalItems)
                .totalAmount(o.getTotalAmount())
                .assigneeId(o.getAssignee() != null ? o.getAssignee().getId() : null)
                .assigneeName(o.getAssignee() != null ? o.getAssignee().getFullName() : null)
                .leaseExpiresAt(o.getLeaseExpiresAt())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
