package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.dto.AddCartItemRequest;
import com.ravan.SpringBootLab.dto.CartItemResponse;
import com.ravan.SpringBootLab.dto.UpdateCartItemRequest;
import com.ravan.SpringBootLab.exception.CartItemNotFoundException;
import com.ravan.SpringBootLab.exception.InsufficientStockException;
import com.ravan.SpringBootLab.exception.ProductNotFoundException;
import com.ravan.SpringBootLab.exception.UserNotFoundException;
import com.ravan.SpringBootLab.model.CartItem;
import com.ravan.SpringBootLab.model.Product;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.CartItemRepository;
import com.ravan.SpringBootLab.repository.ProductRepository;
import com.ravan.SpringBootLab.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    private CartService cartService;
    private User user;
    private Product product;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        cartService = new CartService(
                cartItemRepository,
                userRepository,
                productRepository
        );

        user = new User("Ravan", "ravan@example.com", "backend", "hash", "USER");
        user.setId(1);

        product = new Product(
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("1000.00"),
                10
        );
        product.setId(2);

        cartItem = new CartItem(user, product, 2);
        cartItem.setId(3);
        cartItem.setCreatedAt(LocalDateTime.of(2026, 8, 5, 0, 30));
        cartItem.setUpdatedAt(LocalDateTime.of(2026, 8, 5, 0, 30));
    }

    @Test
    void addItemToCartCreatesNewCartItem() {
        AddCartItemRequest request = mock(AddCartItemRequest.class);
        when(request.getProductId()).thenReturn(2);
        when(request.getQuantity()).thenReturn(3);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(productRepository.findById(2)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByUserAndProduct(user, product))
                .thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem saved = invocation.getArgument(0);
            saved.setId(3);
            return saved;
        });

        CartItemResponse response = cartService.addItemToCart(1, request);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());

        CartItem saved = captor.getValue();
        assertSame(user, saved.getUser());
        assertSame(product, saved.getProduct());
        assertEquals(3, saved.getQuantity());

        assertEquals(3, response.getId());
        assertEquals(1, response.getUserId());
        assertEquals(2, response.getProductId());
        assertEquals(3, response.getQuantity());
        assertEquals(new BigDecimal("3000.00"), response.getSubtotal());
    }

    @Test
    void addItemToCartIncrementsExistingQuantity() {
        AddCartItemRequest request = mock(AddCartItemRequest.class);
        when(request.getProductId()).thenReturn(2);
        when(request.getQuantity()).thenReturn(3);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(productRepository.findById(2)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByUserAndProduct(user, product))
                .thenReturn(Optional.of(cartItem));
        when(cartItemRepository.save(cartItem)).thenReturn(cartItem);

        LocalDateTime previousUpdatedAt = cartItem.getUpdatedAt();
        CartItemResponse response = cartService.addItemToCart(1, request);

        assertEquals(5, cartItem.getQuantity());
        assertNotNull(cartItem.getUpdatedAt());
        assertNotEquals(previousUpdatedAt, cartItem.getUpdatedAt());
        assertEquals(5, response.getQuantity());
        assertEquals(new BigDecimal("5000.00"), response.getSubtotal());
        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void addItemToCartThrowsWhenUserMissing() {
        AddCartItemRequest request = mock(AddCartItemRequest.class);
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> cartService.addItemToCart(99, request)
        );

        verifyNoInteractions(productRepository, cartItemRepository);
    }

    @Test
    void addItemToCartThrowsWhenProductMissing() {
        AddCartItemRequest request = mock(AddCartItemRequest.class);
        when(request.getProductId()).thenReturn(404);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(productRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> cartService.addItemToCart(1, request)
        );

        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void addItemToCartThrowsWhenCombinedQuantityExceedsStock() {
        AddCartItemRequest request = mock(AddCartItemRequest.class);
        when(request.getProductId()).thenReturn(2);
        when(request.getQuantity()).thenReturn(9);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(productRepository.findById(2)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByUserAndProduct(user, product))
                .thenReturn(Optional.of(cartItem));

        assertThrows(
                InsufficientStockException.class,
                () -> cartService.addItemToCart(1, request)
        );

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void getCartItemsMapsAllItems() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(cartItemRepository.findByUser(user)).thenReturn(List.of(cartItem));

        List<CartItemResponse> responses = cartService.getCartItems(1);

        assertEquals(1, responses.size());
        CartItemResponse response = responses.get(0);
        assertEquals(3, response.getId());
        assertEquals("Keyboard", response.getProductName());
        assertEquals(new BigDecimal("2000.00"), response.getSubtotal());
    }

    @Test
    void getCartItemsThrowsWhenUserMissing() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> cartService.getCartItems(99)
        );

        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void updateCartItemUpdatesQuantity() {
        UpdateCartItemRequest request = mock(UpdateCartItemRequest.class);
        when(request.getQuantity()).thenReturn(4);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(cartItemRepository.findById(3)).thenReturn(Optional.of(cartItem));
        when(cartItemRepository.save(cartItem)).thenReturn(cartItem);

        LocalDateTime previousUpdatedAt = cartItem.getUpdatedAt();
        CartItemResponse response = cartService.updateCartItem(1, 3, request);

        assertEquals(4, cartItem.getQuantity());
        assertNotNull(cartItem.getUpdatedAt());
        assertNotEquals(previousUpdatedAt, cartItem.getUpdatedAt());
        assertEquals(4, response.getQuantity());
        assertEquals(new BigDecimal("4000.00"), response.getSubtotal());
        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void updateCartItemThrowsWhenItemMissing() {
        UpdateCartItemRequest request = mock(UpdateCartItemRequest.class);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(cartItemRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(
                CartItemNotFoundException.class,
                () -> cartService.updateCartItem(1, 404, request)
        );

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void updateCartItemRejectsItemOwnedByAnotherUser() {
        User anotherUser = new User("Other", "other@example.com", "backend", "hash", "USER");
        anotherUser.setId(2);
        CartItem otherCartItem = new CartItem(anotherUser, product, 1);
        otherCartItem.setId(4);

        UpdateCartItemRequest request = mock(UpdateCartItemRequest.class);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(cartItemRepository.findById(4)).thenReturn(Optional.of(otherCartItem));

        assertThrows(
                CartItemNotFoundException.class,
                () -> cartService.updateCartItem(1, 4, request)
        );

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void updateCartItemThrowsWhenQuantityExceedsStock() {
        UpdateCartItemRequest request = mock(UpdateCartItemRequest.class);
        when(request.getQuantity()).thenReturn(11);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(cartItemRepository.findById(3)).thenReturn(Optional.of(cartItem));

        assertThrows(
                InsufficientStockException.class,
                () -> cartService.updateCartItem(1, 3, request)
        );

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void deleteCartItemDeletesOwnedItem() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(cartItemRepository.findById(3)).thenReturn(Optional.of(cartItem));

        cartService.deleteCartItem(1, 3);

        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void deleteCartItemRejectsItemOwnedByAnotherUser() {
        User anotherUser = new User("Other", "other@example.com", "backend", "hash", "USER");
        anotherUser.setId(2);
        CartItem otherCartItem = new CartItem(anotherUser, product, 1);
        otherCartItem.setId(4);

        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(cartItemRepository.findById(4)).thenReturn(Optional.of(otherCartItem));

        assertThrows(
                CartItemNotFoundException.class,
                () -> cartService.deleteCartItem(1, 4)
        );

        verify(cartItemRepository, never()).delete(any());
    }
}
