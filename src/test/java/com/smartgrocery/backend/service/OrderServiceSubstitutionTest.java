package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.CreateOrderRequest;
import com.smartgrocery.backend.dto.OrderDto;
import com.smartgrocery.backend.dto.OrderItemRequest;
import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceSubstitutionTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserAddressRepository userAddressRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private InventoryStockRepository inventoryStockRepository;
    @Mock private NotificationService notificationService;
    @Mock private WarehouseRepository warehouseRepository;

    private OrderService newService() {
        OrderService s = new OrderService();
        ReflectionTestUtils.setField(s, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(s, "orderItemRepository", orderItemRepository);
        ReflectionTestUtils.setField(s, "cartRepository", cartRepository);
        ReflectionTestUtils.setField(s, "cartItemRepository", cartItemRepository);
        ReflectionTestUtils.setField(s, "userRepository", userRepository);
        ReflectionTestUtils.setField(s, "userAddressRepository", userAddressRepository);
        ReflectionTestUtils.setField(s, "paymentRepository", paymentRepository);
        ReflectionTestUtils.setField(s, "variantRepository", variantRepository);
        ReflectionTestUtils.setField(s, "inventoryStockRepository", inventoryStockRepository);
        ReflectionTestUtils.setField(s, "notificationService", notificationService);
        ReflectionTestUtils.setField(s, "warehouseRepository", warehouseRepository);
        return s;
    }

    @Test
    void createOrderPersistsAllowSubstitutionFlag() {
        OrderService service = newService();

        User user = User.builder().id(1L).fullName("U").build();
        UserAddress addr = UserAddress.builder().id(10L).build();
        when(userAddressRepository.findById(10L)).thenReturn(Optional.of(addr));

        Warehouse wh = Warehouse.builder().id(1L).code("WH").name("Kho").location("HCM").build();
        when(warehouseRepository.findAll()).thenReturn(List.of(wh));

        Category cat = Category.builder().id(2L).categoryCode("C").name("Cat").build();

        Product p1 = Product.builder().id(101L).productCode("P101").name("Trứng gà").category(cat).status("ACTIVE").build();
        ProductVariant v1 = ProductVariant.builder().id(1001L).product(p1).sku("SKU-101").variantName("V1").unit("PACK").netPrice(BigDecimal.valueOf(35000)).status("ACTIVE").build();

        Product p2 = Product.builder().id(205L).productCode("P205").name("Sữa tươi").category(cat).status("ACTIVE").build();
        ProductVariant v2 = ProductVariant.builder().id(1002L).product(p2).sku("SKU-205").variantName("V2").unit("BOX").netPrice(BigDecimal.valueOf(32000)).status("ACTIVE").build();

        when(variantRepository.findById(1001L)).thenReturn(Optional.of(v1));
        when(variantRepository.findById(1002L)).thenReturn(Optional.of(v2));

        InventoryStock stock1 = InventoryStock.builder().id(1L).warehouse(wh).variant(v1).availableQuantity(50).reservedQuantity(0).build();
        InventoryStock stock2 = InventoryStock.builder().id(2L).warehouse(wh).variant(v2).availableQuantity(50).reservedQuantity(0).build();
        when(inventoryStockRepository.findByWarehouseIdAndVariantId(1L, 1001L)).thenReturn(Optional.of(stock1));
        when(inventoryStockRepository.findByWarehouseIdAndVariantId(1L, 1002L)).thenReturn(Optional.of(stock2));

        when(inventoryStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(999L);
            return o;
        });

        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> {
            OrderItem it = inv.getArgument(0);
            if (it.getId() == null) it.setId(500L);
            return it;
        });

        CreateOrderRequest req = new CreateOrderRequest();
        req.setAddressId(10L);
        req.setPaymentMethod("COD");
        req.setCustomerNote("note");
        req.setItems(List.of(
                OrderItemRequest.builder().variantId(1001L).quantity(1).allowSubstitution(true).build(),
                OrderItemRequest.builder().variantId(1002L).quantity(1).allowSubstitution(false).build(),
                OrderItemRequest.builder().variantId(1002L).quantity(1).allowSubstitution(null).build()
        ));

        OrderDto dto = service.createOrder(user, req);
        assertNotNull(dto);
        assertNotNull(dto.getItems());
        assertEquals(3, dto.getItems().size());
        assertTrue(dto.getItems().get(0).getAllowSubstitution());
        assertFalse(dto.getItems().get(1).getAllowSubstitution());
        assertFalse(dto.getItems().get(2).getAllowSubstitution());

        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository, times(3)).save(captor.capture());
        List<OrderItem> saved = captor.getAllValues();
        assertTrue(saved.get(0).getAllowSubstitution());
        assertFalse(saved.get(1).getAllowSubstitution());
        assertFalse(saved.get(2).getAllowSubstitution());
    }

    @Test
    void checkoutFromDbCartUsesAllowSubstitutionFromCartItemsWhenRequestItemsMissing() {
        OrderService service = newService();

        User user = User.builder().id(1L).fullName("U").build();
        UserAddress addr = UserAddress.builder().id(10L).build();
        when(userAddressRepository.findById(10L)).thenReturn(Optional.of(addr));

        Warehouse wh = Warehouse.builder().id(1L).code("WH").name("Kho").location("HCM").build();
        when(warehouseRepository.findAll()).thenReturn(List.of(wh));

        Category cat = Category.builder().id(2L).categoryCode("C").name("Cat").build();
        Product p1 = Product.builder().id(101L).productCode("P101").name("Trứng gà").category(cat).status("ACTIVE").build();
        ProductVariant v1 = ProductVariant.builder().id(1001L).product(p1).sku("SKU-101").variantName("V1").unit("PACK").netPrice(BigDecimal.valueOf(35000)).status("ACTIVE").build();
        Product p2 = Product.builder().id(205L).productCode("P205").name("Sữa tươi").category(cat).status("ACTIVE").build();
        ProductVariant v2 = ProductVariant.builder().id(1002L).product(p2).sku("SKU-205").variantName("V2").unit("BOX").netPrice(BigDecimal.valueOf(32000)).status("ACTIVE").build();

        Cart cart = Cart.builder().id(77L).user(user).build();
        CartItem ci1 = CartItem.builder().id(1L).cart(cart).variant(v1).quantity(1).allowSubstitution(true).build();
        CartItem ci2 = CartItem.builder().id(2L).cart(cart).variant(v2).quantity(1).allowSubstitution(false).build();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_Id(77L)).thenReturn(List.of(ci1, ci2));

        when(variantRepository.findById(1001L)).thenReturn(Optional.of(v1));
        when(variantRepository.findById(1002L)).thenReturn(Optional.of(v2));

        InventoryStock stock1 = InventoryStock.builder().id(1L).warehouse(wh).variant(v1).availableQuantity(50).reservedQuantity(0).build();
        InventoryStock stock2 = InventoryStock.builder().id(2L).warehouse(wh).variant(v2).availableQuantity(50).reservedQuantity(0).build();
        when(inventoryStockRepository.findByWarehouseIdAndVariantId(1L, 1001L)).thenReturn(Optional.of(stock1));
        when(inventoryStockRepository.findByWarehouseIdAndVariantId(1L, 1002L)).thenReturn(Optional.of(stock2));

        when(inventoryStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(999L);
            return o;
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> {
            OrderItem it = inv.getArgument(0);
            if (it.getId() == null) it.setId(500L);
            return it;
        });

        CreateOrderRequest req = new CreateOrderRequest();
        req.setAddressId(10L);
        req.setPaymentMethod("COD");
        req.setCustomerNote("note");
        req.setItems(null);

        OrderDto dto = service.createOrder(user, req);
        assertNotNull(dto);
        assertNotNull(dto.getItems());
        assertEquals(2, dto.getItems().size());
        assertTrue(dto.getItems().get(0).getAllowSubstitution());
        assertFalse(dto.getItems().get(1).getAllowSubstitution());
    }
}
