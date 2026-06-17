package com.ravan.SpringBootLab.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Integer orderId) {
        super("Payment not found for order id: " + orderId);
    }
}