package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AddToCartRequest;
import com.smartgrocery.backend.dto.CartDto;
import com.smartgrocery.backend.entity.Cart;
import com.smartgrocery.backend.entity.CartItem;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.CartItemRepository;
import com.smartgrocery.backend.repository.jpa.CartRepository;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {

    @Test
    void batchAddToCartMergesExistingVariantEvenWhenAiMetadataDiffers() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userPrincipal(5L), null, List.of())
        );

        CartService service = new CartService();
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        ProductVariantRepository productVariantRepository = mock(ProductVariantRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        InventoryStockRepository inventoryStockRepository = mock(InventoryStockRepository.class);

        inject(service, "cartRepository", cartRepository);
        inject(service, "cartItemRepository", cartItemRepository);
        inject(service, "productVariantRepository", productVariantRepository);
        inject(service, "userRepository", userRepository);
        inject(service, "inventoryStockRepository", inventoryStockRepository);

        User user = User.builder().id(5L).email("test@example.com").status("ACTIVE").build();
        Cart cart = Cart.builder().id(9L).user(user).build();
        Product product = Product.builder().id(100L).name("Banh pho kho").status("ACTIVE").image("img.jpg").build();
        ProductVariant variant = ProductVariant.builder()
                .id(712L)
                .product(product)
                .netPrice(BigDecimal.valueOf(25000))
                .unit("goi")
                .status("ACTIVE")
                .build();
        CartItem existingItem = CartItem.builder()
                .id(1L)
                .cart(cart)
                .variant(variant)
                .quantity(1)
                .source("MANUAL")
                .aiListCode("")
                .build();

        AddToCartRequest request = new AddToCartRequest();
        request.setVariantId(712L);
        request.setQuantity(2);
        request.setSource("AI");
        request.setAiListCode("meal-001");
        request.setAiListName("Pho Bo");
        request.setAllowSubstitution(true);

        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(productVariantRepository.findAllById(List.of(712L))).thenReturn(List.of(variant));
        when(cartItemRepository.findByCart_IdAndVariant_Id(cart.getId(), variant.getId()))
                .thenReturn(Optional.of(existingItem));
        when(cartItemRepository.findByCart_Id(cart.getId())).thenReturn(List.of(existingItem));
        when(inventoryStockRepository.sumAvailableByVariantId(variant.getId())).thenReturn(10L);

        CartDto result = service.batchAddToCart(user, List.of(request));

        assertEquals(3, existingItem.getQuantity());
        assertEquals("AI", existingItem.getSource());
        assertEquals("meal-001", existingItem.getAiListCode());
        assertEquals("Pho Bo", existingItem.getAiListName());
        assertEquals(1, result.getItems().size());

        verify(cartItemRepository).save(existingItem);
        verify(cartItemRepository, never()).findByCart_IdAndVariant_IdAndSourceAndAiListCode(
                cart.getId(), variant.getId(), "AI", "meal-001"
        );

        SecurityContextHolder.clearContext();
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private User userPrincipal(Long id) {
        return User.builder()
                .id(id)
                .email("principal@example.com")
                .status("ACTIVE")
                .build();
    }
}
