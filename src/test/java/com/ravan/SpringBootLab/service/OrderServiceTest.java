package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.dto.OrderResponse;
import com.ravan.SpringBootLab.event.OrderCreatedEvent;
import com.ravan.SpringBootLab.exception.EmptyCartException;
import com.ravan.SpringBootLab.exception.InsufficientStockException;
import com.ravan.SpringBootLab.exception.InvalidOrderStatusException;
import com.ravan.SpringBootLab.exception.OrderNotFoundException;
import com.ravan.SpringBootLab.exception.UserNotFoundException;
import com.ravan.SpringBootLab.model.CartItem;
import com.ravan.SpringBootLab.model.Order;
import com.ravan.SpringBootLab.model.OrderItem;
import com.ravan.SpringBootLab.model.OrderStatus;
import com.ravan.SpringBootLab.model.Product;
import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.CartItemRepository;
import com.ravan.SpringBootLab.repository.OrderItemRepository;
import com.ravan.SpringBootLab.repository.OrderRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OutboxEventService outboxEventService;

    private OrderService orderService;
    private User user;
    private Product product;
    private CartItem cartItem;
    private Order order;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                cartItemRepository,
                userRepository,
                productRepository,
                outboxEventService
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
        product.setVersion(0);

        cartItem = new CartItem(user, product, 2);
        cartItem.setId(3);

        order = new Order(user, new BigDecimal("2000.00"), OrderStatus.PENDING);
        order.setId(4);
        order.setCreatedAt(LocalDateTime.of(2026, 8, 5, 0, 30));
        order.setUpdatedAt(LocalDateTime.of(2026, 8, 5, 0, 30));

        orderItem = new OrderItem(
                order,
                product,
                product.getName(),
                product.getPrice(),
                2,
                new BigDecimal("2000.00")
        );
        orderItem.setId(5);
    }

    @Test
    void createOrderFromCartCreatesOrderItemsReducesStockAndClearsCart() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(cartItemRepository.findByUser(user)).thenReturn(List.of(cartItem));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(4);
            return saved;
        });
        when(orderRepository.findById(4)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrder(any(Order.class)))
                .thenReturn(List.of(orderItem));

        OrderResponse response = orderService.createOrderFromCart(1);

        assertEquals(4, response.getId());
        assertEquals(1, response.getUserId());
        assertEquals(new BigDecimal("2000.00"), response.getTotalAmount());
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertEquals(1, response.getItems().size());

        assertEquals(8, product.getStock());
        verify(productRepository).save(product);

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository).save(itemCaptor.capture());
        OrderItem savedItem = itemCaptor.getValue();
        assertEquals("Keyboard", savedItem.getProductName());
        assertEquals(new BigDecimal("1000.00"), savedItem.getProductPrice());
        assertEquals(2, savedItem.getQuantity());
        assertEquals(new BigDecimal("2000.00"), savedItem.getSubtotal());

        verify(cartItemRepository).deleteAll(List.of(cartItem));
        verify(outboxEventService).saveEvent(
                eq("ORDER"),
                eq("4"),
                eq("ORDER_CREATED"),
                anyString(),
                any(OrderCreatedEvent.class)
        );
    }

    @Test
    void createOrderFromCartThrowsWhenUserMissing() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> orderService.createOrderFromCart(99)
        );

        verifyNoInteractions(
                cartItemRepository,
                orderRepository,
                orderItemRepository,
                productRepository,
                outboxEventService
        );
    }

    @Test
    void createOrderFromCartThrowsWhenCartEmpty() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(cartItemRepository.findByUser(user)).thenReturn(List.of());

        assertThrows(
                EmptyCartException.class,
                () -> orderService.createOrderFromCart(1)
        );

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderItemRepository, productRepository, outboxEventService);
    }

    @Test
    void createOrderFromCartThrowsWhenStockInsufficient() {
        product.setStock(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(cartItemRepository.findByUser(user)).thenReturn(List.of(cartItem));

        assertThrows(
                InsufficientStockException.class,
                () -> orderService.createOrderFromCart(1)
        );

        verify(orderRepository, never()).save(any());
        verify(productRepository, never()).save(any());
        verifyNoInteractions(orderItemRepository, outboxEventService);
    }

    @Test
    void getOrdersByUserMapsOrdersAndItems() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(orderRepository.findByUser(user)).thenReturn(List.of(order));
        when(orderItemRepository.findByOrder(order)).thenReturn(List.of(orderItem));

        List<OrderResponse> responses = orderService.getOrdersByUser(1);

        assertEquals(1, responses.size());
        OrderResponse response = responses.get(0);
        assertEquals(4, response.getId());
        assertEquals(1, response.getUserId());
        assertEquals(new BigDecimal("2000.00"), response.getTotalAmount());
        assertEquals(1, response.getItems().size());
        assertEquals(2, response.getItems().get(0).getProductId());
    }

    @Test
    void getOrdersByUserThrowsWhenUserMissing() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> orderService.getOrdersByUser(99)
        );

        verifyNoInteractions(orderRepository, orderItemRepository);
    }

    @Test
    void getOrderByIdThrowsWhenMissing() {
        when(orderRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(
                OrderNotFoundException.class,
                () -> orderService.getOrderById(404)
        );

        verifyNoInteractions(orderItemRepository);
    }

    @Test
    void cancelOrderRestoresStockAndMarksOrderCancelled() {
        when(orderRepository.findByIdForUpdate(4)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrder(order)).thenReturn(List.of(orderItem));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.cancelOrder(4);

        assertEquals(12, product.getStock());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertNotNull(order.getUpdatedAt());
        assertEquals(OrderStatus.CANCELLED, response.getStatus());

        verify(productRepository).save(product);
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrderRejectsNonPendingOrder() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(4)).thenReturn(Optional.of(order));

        assertThrows(
                InvalidOrderStatusException.class,
                () -> orderService.cancelOrder(4)
        );

        verifyNoInteractions(productRepository);
        verify(orderRepository, never()).save(any());
    }
}
