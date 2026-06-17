package com.ravan.SpringBootLab.exception;

public class EmptyCartException extends RuntimeException {

    public EmptyCartException(Integer userId) {
        super("Cart is empty for user id: " + userId);
    }
}