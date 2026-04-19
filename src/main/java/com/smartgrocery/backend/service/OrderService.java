package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.CreateOrderRequest;
import com.smartgrocery.backend.dto.OrderDto;
import com.smartgrocery.backend.dto.OrderItemDto;
import com.smartgrocery.backend.dto.OrderItemRequest;
import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.repository.*;
import com.smartgrocery.backend.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private UserRepository userRepository;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Transactional(rollbackFor = Exception.class)
    public OrderDto createOrder(User user, CreateOrderRequest request) {

        UserAddress address = (request.getAddressId() != null)
                ? userAddressRepository.findById(request.getAddressId())
                    .orElseThrow(() -> new RuntimeException("Địa chỉ giao hàng không tồn tại"))
                : null;

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Giỏ hàng rỗng, không thể tạo đơn hàng");
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

            // Snapshot price
            BigDecimal unitPrice = variant.getNetPrice();
            BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder)
                    .variant(variant)
                    .productName(variant.getProduct().getName())
                    .variantName(variant.getVariantName())
                    .sku(variant.getSku())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(itemTotal)
                    .totalPrice(itemTotal)
                    .allowSubstitution(Boolean.TRUE.equals(itemReq.getAllowSubstitution()))
                    .build();


            orderItems.add(orderItemRepository.save(orderItem));
            
            // 3. Update Stock
            stock.setAvailableQuantity(stock.getAvailableQuantity() - itemReq.getQuantity());
            inventoryStockRepository.save(stock);

            subtotal = subtotal.add(itemTotal);
        }

        // 4. Finalize Totals
        savedOrder.setOrderItems(orderItems);
        savedOrder.setSubtotal(subtotal);
        savedOrder.setTotalAmount(subtotal.add(savedOrder.getShippingFee()));
        orderRepository.save(savedOrder);

        // 5. Sync: Clear DB Cart for this user
        cartRepository.findByUserId(user.getId()).ifPresent(cart -> {
            cartItemRepository.deleteAll(cartItemRepository.findByCart_Id(cart.getId()));
        });

        // 6. Create Initial Payment Record
        Payment payment = Payment.builder()
                .order(savedOrder)
                .paymentMethod(request.getPaymentMethod())
                .amount(savedOrder.getTotalAmount())
                .status("PENDING")
                .build();
        paymentRepository.save(payment);

        // 7. Notify Staff about new order
        try {
            List<User> staffMembers = userRepository.findByRole_Name("STAFF");
            notificationService.notifyStaff(
                    "Đơn hàng mới: " + savedOrder.getOrderNumber(),
                    "Khách hàng " + savedOrder.getUser().getFullName() + " vừa đặt một đơn hàng mới.",
                    "NEW_ORDER",
                    staffMembers
            );
        } catch (Exception e) {
            // Don't fail the order if notification fails
        }

        return mapToDto(savedOrder);
    }

    public List<OrderDto> getUserOrders(Long userId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        return orderRepository.findByUser_Id(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public OrderDto getOrderDetail(Long userId, Long orderId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getUser().getId().equals(userId)) {
            throw new RuntimeException("Forbidden");
        }
        return mapToDto(order);
    }

    private OrderDto mapToDto(Order order) {
        return OrderDto.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
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
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(order.getOrderItems() != null ? order.getOrderItems().stream().map(item -> OrderItemDto.builder()
                        .id(item.getId())
                        .variantId(item.getVariant().getId())
                        .productName(item.getProductName())
                        .variantName(item.getVariantName())
                        .sku(item.getSku())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .discountAmount(item.getDiscountAmount())
                        .totalPrice(item.getTotalPrice())
                        .allowSubstitution(item.getAllowSubstitution())
                        .build()).collect(Collectors.toList()) : null)
                .build();
    }
}
