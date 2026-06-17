package com.ravan.SpringBootLab.exception;

public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(Integer id) {
        super("Cart item not found with id: " + id);
    }
}