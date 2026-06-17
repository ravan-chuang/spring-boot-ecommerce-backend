package com.ravan.SpringBootLab.exception;

public class OrderAlreadyPaidException extends RuntimeException {

    public OrderAlreadyPaidException(Integer orderId) {
        super("Order already paid with id: " + orderId);
    }
}