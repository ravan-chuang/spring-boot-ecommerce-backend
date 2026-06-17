package com.ravan.SpringBootLab.dto;

import com.ravan.SpringBootLab.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public class CreatePaymentRequest {

    @NotNull(message = "payment method is required")
    private PaymentMethod method;

    public PaymentMethod getMethod() {
        return method;
    }
}