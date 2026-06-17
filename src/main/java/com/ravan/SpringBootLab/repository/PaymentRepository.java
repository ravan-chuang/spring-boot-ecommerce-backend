package com.ravan.SpringBootLab.repository;

import com.ravan.SpringBootLab.model.Order;
import com.ravan.SpringBootLab.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Optional<Payment> findByOrder(Order order);

    boolean existsByOrder(Order order);
}