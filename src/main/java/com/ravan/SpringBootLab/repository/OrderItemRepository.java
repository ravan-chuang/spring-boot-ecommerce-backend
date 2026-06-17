package com.ravan.SpringBootLab.repository;

import com.ravan.SpringBootLab.model.Order;
import com.ravan.SpringBootLab.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {

    List<OrderItem> findByOrder(Order order);
}