package com.ravan.SpringBootLab.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentPaidEvent {

    private Integer paymentId;
    private Integer orderId;
    private BigDecimal amount;
    private String method;
    private LocalDateTime paidAt;

    public PaymentPaidEvent() {
    }

    public PaymentPaidEvent(
            Integer paymentId,
            Integer orderId,
            BigDecimal amount,
            String method,
            LocalDateTime paidAt
    ) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.method = method;
        this.paidAt = paidAt;
    }

    public Integer getPaymentId() {
        return paymentId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMethod() {
        return method;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaymentId(Integer paymentId) {
        this.paymentId = paymentId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
}