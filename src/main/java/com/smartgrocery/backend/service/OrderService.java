package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.CreateOrderRequest;
import com.smartgrocery.backend.dto.OrderDto;
import com.smartgrocery.backend.dto.OrderItemDto;
import com.smartgrocery.backend.dto.OrderItemRequest;
import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.repository.jpa.*;
import com.smartgrocery.backend.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private AutoOrderDispatchService autoOrderDispatchService;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private UserVoucherUsageRepository userVoucherUsageRepository;

    @Autowired
    private UserClaimedVoucherRepository userClaimedVoucherRepository;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private NotificationService notificationService;

    @Autowired(required = false)
    private Clock clock = Clock.systemDefaultZone();

    @Transactional(rollbackFor = Exception.class)
    public OrderDto createOrder(User user, CreateOrderRequest request) {

        UserAddress address = (request.getAddressId() != null)
                ? userAddressRepository.findById(request.getAddressId())
                    .orElseThrow(() -> new RuntimeException("Địa chỉ giao hàng không tồn tại"))
                : null;

        if (request.getItems() == null || request.getItems().isEmpty()) {
            // Fallback: checkout from persisted cart
            Cart cart = cartRepository.findByUserId(user.getId()).orElse(null);
            if (cart == null) {
                throw new RuntimeException("Giỏ hàng rỗng, không thể tạo đơn hàng");
            }
            List<CartItem> cartItems = cartItemRepository.findByCart_Id(cart.getId());
            if (cartItems.isEmpty()) {
                throw new RuntimeException("Giỏ hàng rỗng, không thể tạo đơn hàng");
            }

            CartAiMetadata aiMetadata = resolveCartAiMetadata(cartItems);
            List<OrderItemRequest> fromCart = cartItems.stream()
                    .map(ci -> OrderItemRequest.builder()
                            .variantId(ci.getVariant().getId())
                            .quantity(ci.getQuantity())
                            .build())
                    .collect(Collectors.toList());
            request.setItems(fromCart);
            request.setAiGenerated(aiMetadata.aiGenerated());
            request.setAiListCode(aiMetadata.aiListCode());
            request.setAiListName(aiMetadata.aiListName());
        } else {
            Cart cart = cartRepository.findByUserId(user.getId()).orElse(null);
            if (cart != null) {
                List<CartItem> cartItems = cartItemRepository.findByCart_Id(cart.getId());
                java.util.Set<Long> orderedVariantIds = request.getItems().stream()
                        .map(OrderItemRequest::getVariantId)
                        .collect(Collectors.toSet());
                List<CartItem> matchingCartItems = cartItems.stream()
                        .filter(ci -> orderedVariantIds.contains(ci.getVariant().getId()))
                        .collect(Collectors.toList());
                CartAiMetadata aiMetadata = resolveCartAiMetadata(matchingCartItems);
                request.setAiGenerated(aiMetadata.aiGenerated());
                request.setAiListCode(aiMetadata.aiListCode());
                request.setAiListName(aiMetadata.aiListName());
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalTime currentTime = now.toLocalTime();
        LocalTime blockStart = LocalTime.of(22, 0);
        LocalTime blockEnd = LocalTime.of(6, 0);
        if (!currentTime.isBefore(blockStart) || currentTime.isBefore(blockEnd)) {
            throw new RuntimeException("Hệ thống tạm ngừng nhận đơn từ 22:00 đến 06:00 sáng hôm sau.");
        }

        // 1. Create Base Order
        Order order = Order.builder()
                .user(user)
                .address(address)
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status("PENDING")
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus("UNPAID")
                .customerNote(request.getCustomerNote())
                .aiGenerated(Boolean.TRUE.equals(request.getAiGenerated()))
                .aiListCode(request.getAiListCode())
                .aiListName(request.getAiListName())
                .subtotal(BigDecimal.ZERO)
                .shippingFee(BigDecimal.valueOf(15000))
                .totalAmount(BigDecimal.ZERO)
                .build();

        Order savedOrder = orderRepository.save(order);
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        // 2. Process Items with Stock Lock & Price Snapshot
        Warehouse warehouse = warehouseRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho hàng"));
        for (OrderItemRequest itemReq : request.getItems()) {
            ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại: " + itemReq.getVariantId()));

            // PESIMISTIC LOCK & Stock Check
            if (variant.getId() == null) throw new RuntimeException("Variant ID is null");
            
            InventoryStock stock = inventoryStockRepository.findByWarehouseIdAndVariantId(warehouse.getId(), variant.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin tồn kho cho: " + variant.getVariantName()));
            
            if (stock.getAvailableQuantity() == null || stock.getAvailableQuantity() < itemReq.getQuantity()) {
                throw new RuntimeException("Sản phẩm [" + variant.getVariantName() + "] đã hết hàng (Chỉ còn " + (stock.getAvailableQuantity() != null ? stock.getAvailableQuantity() : 0) + ")");
            }

            // Snapshot price and discount
            BigDecimal netPrice = variant.getNetPrice();
            BigDecimal compareAtPrice = variant.getCompareAtPrice();
            BigDecimal unitPrice = (compareAtPrice != null && compareAtPrice.compareTo(netPrice) > 0) ? compareAtPrice : netPrice;
            BigDecimal discountPerItem = unitPrice.subtract(netPrice);
            BigDecimal totalItemDiscount = discountPerItem.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            BigDecimal itemTotal = netPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder)
                    .variant(variant)
                    .productName(variant.getProduct().getName())
                    .variantName(variant.getVariantName())
                    .sku(variant.getSku())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(itemSubtotal)
                    .discountAmount(totalItemDiscount)
                    .totalPrice(itemTotal)
                    .build();


            orderItems.add(orderItemRepository.save(orderItem));
            
            // 3. Update Stock
            stock.setAvailableQuantity(stock.getAvailableQuantity() - itemReq.getQuantity());
            inventoryStockRepository.save(stock);

            subtotal = subtotal.add(itemTotal);
        }

        // 4. Apply voucher (optional)
        BigDecimal discount = BigDecimal.ZERO;
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            Voucher voucher = voucherRepository.findByVoucherCode(request.getVoucherCode().trim())
                    .orElseThrow(() -> new RuntimeException("Voucher không tồn tại"));
            validateVoucher(user, voucher, subtotal);
            UserClaimedVoucher claim = userClaimedVoucherRepository.findUsableClaim(user.getId(), voucher.getId(), now)
                .orElseThrow(() -> new RuntimeException("Voucher chưa được nhận hoặc đã hết hạn"));
            discount = computeDiscount(voucher, subtotal);
            voucher.setUsageCount((voucher.getUsageCount() != null ? voucher.getUsageCount() : 0) + 1);
            voucherRepository.save(voucher);
            claim.setUsed(true);
            claim.setUsedAt(now);
            userClaimedVoucherRepository.save(claim);
            upsertUserVoucherUsage(user, voucher);
        }

        // 5. Finalize Totals
        savedOrder.setOrderItems(orderItems);
        savedOrder.setSubtotal(subtotal);
        savedOrder.setDiscountAmount(discount);
        BigDecimal gross = subtotal.add(savedOrder.getShippingFee());
        BigDecimal finalAmount = gross.subtract(discount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) finalAmount = BigDecimal.ZERO;
        savedOrder.setTotalAmount(finalAmount);
        orderRepository.save(savedOrder);

        // 6. Sync: Clear DB Cart for purchased items only
        cartRepository.findByUserId(user.getId()).ifPresent(cart -> {
            List<CartItem> cartItems = cartItemRepository.findByCart_Id(cart.getId());
            java.util.Set<Long> purchasedVariantIds = request.getItems().stream()
                    .map(OrderItemRequest::getVariantId)
                    .collect(Collectors.toSet());
            List<CartItem> purchasedCartItems = cartItems.stream()
                    .filter(ci -> purchasedVariantIds.contains(ci.getVariant().getId()))
                    .collect(Collectors.toList());
            cartItemRepository.deleteAll(purchasedCartItems);
        });

        // 7. Create Initial Payment Record
        Payment payment = Payment.builder()
                .order(savedOrder)
                .paymentMethod(request.getPaymentMethod())
                .amount(savedOrder.getTotalAmount())
                .status("PENDING")
                .build();
        paymentRepository.save(payment);

        // 8. Auto-dispatch: place the order into the staff queue first
        if (autoOrderDispatchService != null) {
            autoOrderDispatchService.tryAutoAssign(savedOrder.getId());
        }

        // 9. Notify Customer of Successful Order Placement
        try {
            if (savedOrder.getUser() != null) {
                notificationService.sendNotification(
                        savedOrder.getUser(),
                        "Đặt hàng thành công",
                        "Đơn hàng " + savedOrder.getOrderNumber() + " của bạn đã được tạo thành công và đang chờ xử lý.",
                        "ORDER_STATUS",
                        java.util.Map.of("route", "/(customer)/orders/" + savedOrder.getId(), "orderId", String.valueOf(savedOrder.getId()), "type", "ORDER_STATUS")
                );
            }
        } catch (Exception e) {
            // Ignore notification failure so it doesn't block transaction completion
        }

        return mapToDto(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getUserOrders(Long userId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        return orderRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderDetail(Long userId, Long orderId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getUser().getId().equals(userId)) {
            throw new RuntimeException("Forbidden");
        }
        return mapToDto(order);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderDetailForAdmin(Long orderId) {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return mapToDto(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderDto cancelOrder(User user, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Đơn hàng không tồn tại"));

        // Only owner or admin can cancel
        if (!order.getUser().getId().equals(user.getId()) && !user.getRole().getName().contains("ADMIN")) {
            throw new RuntimeException("Bạn không có quyền hủy đơn hàng này");
        }

        // Allow cancellation until "Confirmed Packing" (READY_TO_SHIP)
        List<String> cancellableStatuses = List.of("PENDING", "ASSIGNED", "PICKING");
        if (!cancellableStatuses.contains(order.getStatus()) && !user.getRole().getName().contains("ADMIN")) {
            throw new RuntimeException("Đơn hàng đã được đóng gói hoặc đang giao, không thể hủy");
        }

        if ("CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("Đơn hàng đã được hủy trước đó");
        }

        // 1. Update status
        order.setStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());

        // 2. Return stock
        Warehouse warehouse = warehouseRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho hàng"));

        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                InventoryStock stock = inventoryStockRepository.findByWarehouseIdAndVariantId(warehouse.getId(), item.getVariant().getId())
                        .orElse(null);
                if (stock != null) {
                    stock.setAvailableQuantity(stock.getAvailableQuantity() + item.getQuantity());
                    inventoryStockRepository.save(stock);
                }
            }
        }

        Order saved = orderRepository.save(order);
        return mapToDto(saved);
    }

    private OrderDto mapToDto(Order order) {
        UserAddress address = order.getAddress();
        User customer = order.getUser();
        return OrderDto.builder()
                .id(order.getId())
                .userId(customer != null ? customer.getId() : null)
                .addressId(address != null ? address.getId() : null)
                .receiverName(address != null ? address.getReceiverName() : customer != null ? customer.getFullName() : null)
                .receiverPhone(address != null ? address.getReceiverPhone() : customer != null ? customer.getPhone() : null)
                .addressLine(address != null ? 
                        String.format("%s, %s, %s, %s", 
                            address.getStreetAddress(), 
                            address.getWard(), 
                            address.getDistrict(), 
                            address.getCity()) : null)
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
                .aiGenerated(order.getAiGenerated())
                .aiListCode(order.getAiListCode())
                .aiListName(order.getAiListName())
                .rewardVoucher(order.getRewardVoucher() != null ? voucherService.toDtoPublic(order.getRewardVoucher()) : null)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(order.getOrderItems() != null ? order.getOrderItems().stream().map(item -> {
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
                        .imageUrl(item.getVariant() != null && item.getVariant().getProduct() != null ? item.getVariant().getProduct().getImage() : null)
                        .build();
                }).collect(Collectors.toList()) : null)
                .build();
    }

    private void validateVoucher(User user, Voucher voucher, BigDecimal subtotal) {
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(voucher.getActive())) {
            throw new RuntimeException("Voucher đã bị vô hiệu");
        }
        if (voucher.getValidFrom() != null && voucher.getValidFrom().isAfter(now)) {
            throw new RuntimeException("Voucher chưa đến thời gian áp dụng");
        }
        if (voucher.getValidUntil() != null && voucher.getValidUntil().isBefore(now)) {
            throw new RuntimeException("Voucher đã hết hạn");
        }
        if (voucher.getUsageLimit() != null && voucher.getUsageCount() != null && voucher.getUsageCount() >= voucher.getUsageLimit()) {
            throw new RuntimeException("Voucher đã hết lượt sử dụng");
        }
        if (voucher.getMinOrderAmount() != null && subtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new RuntimeException("Đơn hàng chưa đạt mức tối thiểu để áp dụng voucher");
        }
        java.util.Optional<UserVoucherUsage> usageOpt = userVoucherUsageRepository.findByUser_IdAndVoucher_Id(user.getId(), voucher.getId());
        if (usageOpt.isPresent() && usageOpt.get().getUsedCount() != null && usageOpt.get().getUsedCount() >= 1) {
            throw new RuntimeException("Bạn đã sử dụng voucher này rồi");
        }
    }

    private BigDecimal computeDiscount(Voucher voucher, BigDecimal subtotal) {
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal value = voucher.getDiscountValue() != null ? voucher.getDiscountValue() : BigDecimal.ZERO;
        BigDecimal discount;
        if ("PERCENTAGE".equalsIgnoreCase(voucher.getDiscountType()) || "PERCENT".equalsIgnoreCase(voucher.getDiscountType())) {
            discount = subtotal.multiply(value).divide(BigDecimal.valueOf(100));
        } else {
            discount = value;
        }
        if (voucher.getMaxDiscountAmount() != null && discount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
            discount = voucher.getMaxDiscountAmount();
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        return discount.max(BigDecimal.ZERO);
    }

    private void upsertUserVoucherUsage(User user, Voucher voucher) {
        UserVoucherUsage usage = userVoucherUsageRepository.findByUser_IdAndVoucher_Id(user.getId(), voucher.getId())
                .orElse(UserVoucherUsage.builder().user(user).voucher(voucher).usedCount(0).build());
        usage.setUsedCount((usage.getUsedCount() != null ? usage.getUsedCount() : 0) + 1);
        usage.setUpdatedAt(LocalDateTime.now());
        userVoucherUsageRepository.save(usage);
    }

    private CartAiMetadata resolveCartAiMetadata(List<CartItem> cartItems) {
        CartItem aiItem = cartItems.stream()
                .filter(item -> "AI".equalsIgnoreCase(item.getSource()))
                .findFirst()
                .orElse(null);
        if (aiItem == null) {
            return new CartAiMetadata(false, null, null);
        }
        return new CartAiMetadata(true, blankToNull(aiItem.getAiListCode()), blankToNull(aiItem.getAiListName()));
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record CartAiMetadata(boolean aiGenerated, String aiListCode, String aiListName) {}
}
