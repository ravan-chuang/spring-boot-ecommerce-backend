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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartService(
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            ProductRepository productRepository
    ) {
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    public CartItemResponse addItemToCart(Integer userId, AddCartItemRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));

        CartItem cartItem = cartItemRepository.findByUserAndProduct(user, product)
                .orElse(null);

        int newQuantity;

        if (cartItem == null) {
            newQuantity = request.getQuantity();
        } else {
            newQuantity = cartItem.getQuantity() + request.getQuantity();
        }

        if (newQuantity > product.getStock()) {
            throw new InsufficientStockException(
                    "Insufficient stock. Available stock: " + product.getStock()
            );
        }

        if (cartItem == null) {
            cartItem = new CartItem(user, product, request.getQuantity());
        } else {
            cartItem.setQuantity(newQuantity);
            cartItem.setUpdatedAt(LocalDateTime.now());
        }

        CartItem savedCartItem = cartItemRepository.save(cartItem);

        return toCartItemResponse(savedCartItem);
    }

    public List<CartItemResponse> getCartItems(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return cartItemRepository.findByUser(user)
                .stream()
                .map(this::toCartItemResponse)
                .collect(Collectors.toList());
    }

    public CartItemResponse updateCartItem(
            Integer userId,
            Integer cartItemId,
            UpdateCartItemRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));

        if (!cartItem.getUser().getId().equals(user.getId())) {
            throw new CartItemNotFoundException(cartItemId);
        }

        Product product = cartItem.getProduct();

        if (request.getQuantity() > product.getStock()) {
            throw new InsufficientStockException(
                    "Insufficient stock. Available stock: " + product.getStock()
            );
        }

        cartItem.setQuantity(request.getQuantity());
        cartItem.setUpdatedAt(LocalDateTime.now());

        CartItem savedCartItem = cartItemRepository.save(cartItem);

        return toCartItemResponse(savedCartItem);
    }

    public void deleteCartItem(Integer userId, Integer cartItemId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));

        if (!cartItem.getUser().getId().equals(user.getId())) {
            throw new CartItemNotFoundException(cartItemId);
        }

        cartItemRepository.delete(cartItem);
    }

    private CartItemResponse toCartItemResponse(CartItem cartItem) {
        BigDecimal subtotal = cartItem.getProduct().getPrice()
                .multiply(BigDecimal.valueOf(cartItem.getQuantity()));

        return new CartItemResponse(
                cartItem.getId(),
                cartItem.getUser().getId(),
                cartItem.getProduct().getId(),
                cartItem.getProduct().getName(),
                cartItem.getProduct().getPrice(),
                cartItem.getQuantity(),
                subtotal,
                cartItem.getCreatedAt(),
                cartItem.getUpdatedAt()
        );
    }
}