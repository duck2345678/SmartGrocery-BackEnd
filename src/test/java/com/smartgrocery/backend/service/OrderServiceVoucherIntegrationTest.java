package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.CreateOrderRequest;
import com.smartgrocery.backend.dto.OrderItemRequest;
import com.smartgrocery.backend.entity.InventoryStock;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.Payment;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserAddress;
import com.smartgrocery.backend.entity.UserClaimedVoucher;
import com.smartgrocery.backend.entity.Voucher;
import com.smartgrocery.backend.entity.Warehouse;
import com.smartgrocery.backend.repository.jpa.CartItemRepository;
import com.smartgrocery.backend.repository.jpa.CartRepository;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.OrderItemRepository;
import com.smartgrocery.backend.repository.jpa.OrderRepository;
import com.smartgrocery.backend.repository.jpa.PaymentRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.UserAddressRepository;
import com.smartgrocery.backend.repository.jpa.UserClaimedVoucherRepository;
import com.smartgrocery.backend.repository.jpa.UserVoucherUsageRepository;
import com.smartgrocery.backend.repository.jpa.VoucherRepository;
import com.smartgrocery.backend.repository.jpa.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {OrderService.class, OrderServiceVoucherIntegrationTest.FixedClockConfig.class})
class OrderServiceVoucherIntegrationTest {

    @Autowired
    private OrderService orderService;

    @MockBean private OrderRepository orderRepository;
    @MockBean private OrderItemRepository orderItemRepository;
    @MockBean private CartRepository cartRepository;
    @MockBean private CartItemRepository cartItemRepository;
    @MockBean private UserAddressRepository userAddressRepository;
    @MockBean private PaymentRepository paymentRepository;
    @MockBean private ProductVariantRepository variantRepository;
    @MockBean private InventoryStockRepository inventoryStockRepository;
    @MockBean private WarehouseRepository warehouseRepository;
    @MockBean private AutoOrderDispatchService autoOrderDispatchService;
    @MockBean private VoucherRepository voucherRepository;
    @MockBean private UserVoucherUsageRepository userVoucherUsageRepository;
    @MockBean private UserClaimedVoucherRepository userClaimedVoucherRepository;
    @MockBean private VoucherService voucherService;
    @MockBean private NotificationService notificationService;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-15T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private User user;
    private UserAddress address;
    private Warehouse warehouse;
    private ProductVariant variant;
    private InventoryStock stock;
    private Voucher voucher;
    private UserClaimedVoucher claim;
    private LocalDateTime fixedNow;

    @BeforeEach
    void setUp() {
        fixedNow = LocalDateTime.of(2026, 6, 15, 12, 0);
        user = User.builder().id(10L).email("customer@smartgrocery.vn").build();
        address = UserAddress.builder()
            .id(20L)
            .user(user)
            .receiverName("Customer")
            .receiverPhone("0900000000")
            .streetAddress("1 Main St")
            .city("HCMC")
            .isDefault(true)
            .build();
        warehouse = Warehouse.builder().id(30L).name("Main Warehouse").build();
        Product product = Product.builder().id(40L).name("Gạo ST25").build();
        variant = ProductVariant.builder()
                .id(50L)
                .variantName("5kg")
                .sku("RICE-5KG")
                .product(product)
                .netPrice(BigDecimal.valueOf(100000))
                .compareAtPrice(BigDecimal.valueOf(120000))
                .build();
        stock = InventoryStock.builder().id(60L).availableQuantity(20).build();
        voucher = Voucher.builder()
                .id(70L)
                .voucherCode("SAVE10")
                .discountType("FIXED_AMOUNT")
                .discountValue(BigDecimal.valueOf(10000))
                .minOrderAmount(BigDecimal.valueOf(50000))
                .usageLimit(5)
                .claimCount(1)
                .usageCount(0)
                .active(true)
                .hidden(false)
                .build();
        claim = UserClaimedVoucher.builder()
                .id(80L)
                .user(user)
                .voucher(voucher)
                .claimedAt(fixedNow.minusHours(1))
                .used(false)
                .status("ACTIVE")
                .expiresAt(fixedNow.plusDays(1))
                .build();

        when(userAddressRepository.findById(20L)).thenReturn(Optional.of(address));
        when(cartRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(warehouseRepository.findAll()).thenReturn(List.of(warehouse));
        when(variantRepository.findById(50L)).thenReturn(Optional.of(variant));
        when(inventoryStockRepository.findByWarehouseIdAndVariantId(30L, 50L)).thenReturn(Optional.of(stock));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryStockRepository.save(any(InventoryStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(voucherRepository.findByVoucherCode("SAVE10")).thenReturn(Optional.of(voucher));
    }

    @Test
    void checkoutRejectsUnclaimedVoucher() {
        when(userClaimedVoucherRepository.findUsableClaim(eq(10L), eq(70L), any(LocalDateTime.class))).thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(20L);
        request.setPaymentMethod("COD");
        request.setVoucherCode("SAVE10");
        request.setItems(List.of(OrderItemRequest.builder().variantId(50L).quantity(1).build()));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(user, request));

        assertEquals("Voucher chưa được nhận hoặc đã hết hạn", ex.getMessage());
    }

    @Test
    void checkoutAcceptsClaimedVoucher() {
        when(userClaimedVoucherRepository.findUsableClaim(eq(10L), eq(70L), any(LocalDateTime.class))).thenReturn(Optional.of(claim));
        when(userClaimedVoucherRepository.save(any(UserClaimedVoucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(20L);
        request.setPaymentMethod("COD");
        request.setVoucherCode("SAVE10");
        request.setItems(List.of(OrderItemRequest.builder().variantId(50L).quantity(1).build()));

        var order = orderService.createOrder(user, request);

        assertEquals("PENDING", order.getStatus());
        assertEquals(10000.0, order.getDiscountAmount().doubleValue());
    }
}
