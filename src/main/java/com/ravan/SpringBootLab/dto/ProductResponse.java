package com.ravan.SpringBootLab.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;

public class ProductResponse implements Serializable{

    private static final long serialVersionUID = 1L;
    
    private Integer id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ProductResponse(
            Integer id,
            String name,
            String description,
            BigDecimal price,
            Integer stock,
            Integer version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public Integer getVersion() {
        return version;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}