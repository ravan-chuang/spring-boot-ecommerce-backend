package com.ravan.SpringBootLab.repository;

import com.ravan.SpringBootLab.model.Order;
import com.ravan.SpringBootLab.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByUser(User user);
}