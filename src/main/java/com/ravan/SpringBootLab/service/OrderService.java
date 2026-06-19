package com.ravan.SpringBootLab.service;

import com.ravan.SpringBootLab.config.KafkaTopicConfig;
import com.ravan.SpringBootLab.event.OrderCreatedEvent;
import com.ravan.SpringBootLab.dto.OrderItemResponse;
import com.ravan.SpringBootLab.dto.OrderResponse;
import com.ravan.SpringBootLab.exception.EmptyCartException;
import com.ravan.SpringBootLab.exception.InsufficientStockException;
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

import com.ravan.SpringBootLab.exception.InvalidOrderStatusException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OutboxEventService outboxEventService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            OutboxEventService outboxEventService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public OrderResponse createOrderFromCart(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        List<CartItem> cartItems = cartItemRepository.findByUser(user);

        if (cartItems.isEmpty()) {
            throw new EmptyCartException(userId);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();

            if (cartItem.getQuantity() > product.getStock()) {
                throw new InsufficientStockException(
                        "Insufficient stock for product: " + product.getName()
                                + ". Available stock: " + product.getStock()
                );
            }

            BigDecimal subtotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            totalAmount = totalAmount.add(subtotal);
        }

        Order order = new Order(user, totalAmount, OrderStatus.PENDING);
        Order savedOrder = orderRepository.save(order);

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();

            product.setStock(product.getStock() - cartItem.getQuantity());
            product.setUpdatedAt(LocalDateTime.now());
            productRepository.save(product);

            BigDecimal subtotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            OrderItem orderItem = new OrderItem(
                    savedOrder,
                    product,
                    product.getName(),
                    product.getPrice(),
                    cartItem.getQuantity(),
                    subtotal
            );

            orderItemRepository.save(orderItem);
        }

        cartItemRepository.deleteAll(cartItems);

        outboxEventService.saveEvent(
                "ORDER",
                String.valueOf(savedOrder.getId()),
                "ORDER_CREATED",
                KafkaTopicConfig.ORDER_CREATED_TOPIC,
                new OrderCreatedEvent(
                        savedOrder.getId(),
                        user.getId(),
                        savedOrder.getTotalAmount(),
                        savedOrder.getCreatedAt()
                )
        );

        return getOrderById(savedOrder.getId());
    }

    @Transactional
    public OrderResponse createOrderFromCartSlow(Integer userId) {
        User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
        
        List<CartItem> cartItems = cartItemRepository.findByUser(user);
        
        if (cartItems.isEmpty()) {
                throw new EmptyCartException(userId);
        }
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        for (CartItem cartItem : cartItems) {
                Product product = cartItem.getProduct();
                
                if (cartItem.getQuantity() > product.getStock()) {
                        throw new InsufficientStockException(
                                "Insufficient stock for product: " + product.getName()
                                + ". Available stock: " + product.getStock()
                        );
                }
                
                BigDecimal subtotal = product.getPrice()
                .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
                
                totalAmount = totalAmount.add(subtotal);
        }
        
        try {
                Thread.sleep(5000);
        } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
        }
        
        Order order = new Order(user, totalAmount, OrderStatus.PENDING);
        Order savedOrder = orderRepository.save(order);
        
        for (CartItem cartItem : cartItems) {
                Product product = cartItem.getProduct();
                
                product.setStock(product.getStock() - cartItem.getQuantity());
                product.setUpdatedAt(LocalDateTime.now());
                
                productRepository.save(product);
                
                BigDecimal subtotal = product.getPrice()
                .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
                
                OrderItem orderItem = new OrderItem(
                        savedOrder,
                        product,
                        product.getName(),
                        product.getPrice(),
                        cartItem.getQuantity(),
                        subtotal
                );
                
                orderItemRepository.save(orderItem);
        }
        
        cartItemRepository.deleteAll(cartItems);

        outboxEventService.saveEvent(
                "ORDER",
                String.valueOf(savedOrder.getId()),
                "ORDER_CREATED",
                KafkaTopicConfig.ORDER_CREATED_TOPIC,
                new OrderCreatedEvent(
                        savedOrder.getId(),
                        user.getId(),
                        savedOrder.getTotalAmount(),
                        savedOrder.getCreatedAt()
                )
        );
        
        return getOrderById(savedOrder.getId());
    }

    public List<OrderResponse> getOrdersByUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return orderRepository.findByUser(user)
                .stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrderById(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Integer orderId) {
        Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
        
        if (order.getStatus() != OrderStatus.PENDING) {
                throw new InvalidOrderStatusException(
                        "Only PENDING orders can be cancelled. Current status: " + order.getStatus()
                );
        }
        
        List<OrderItem> orderItems = orderItemRepository.findByOrder(order);
        
        for (OrderItem orderItem : orderItems) {
                Product product = orderItem.getProduct();
                
                product.setStock(product.getStock() + orderItem.getQuantity());
                product.setUpdatedAt(LocalDateTime.now());
                
                productRepository.save(product);
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        
        Order savedOrder = orderRepository.save(order);
        
        return toOrderResponse(savedOrder);
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> items = orderItemRepository.findByOrder(order)
                .stream()
                .map(this::toOrderItemResponse)
                .collect(Collectors.toList());

        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getTotalAmount(),
                order.getStatus(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toOrderItemResponse(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getProduct().getId(),
                orderItem.getProductName(),
                orderItem.getProductPrice(),
                orderItem.getQuantity(),
                orderItem.getSubtotal()
        );
    }
}