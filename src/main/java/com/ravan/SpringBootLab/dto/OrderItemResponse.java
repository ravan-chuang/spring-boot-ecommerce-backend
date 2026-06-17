package com.ravan.SpringBootLab.dto;

import java.math.BigDecimal;

public class OrderItemResponse {

    private Integer id;
    private Integer productId;
    private String productName;
    private BigDecimal productPrice;
    private Integer quantity;
    private BigDecimal subtotal;

    public OrderItemResponse(
            Integer id,
            Integer productId,
            String productName,
            BigDecimal productPrice,
            Integer quantity,
            BigDecimal subtotal
    ) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    public Integer getId() {
        return id;
    }

    public Integer getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}