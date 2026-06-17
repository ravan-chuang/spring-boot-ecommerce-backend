package com.ravan.SpringBootLab.repository;

import com.ravan.SpringBootLab.model.CartItem;
import com.ravan.SpringBootLab.model.Product;
import com.ravan.SpringBootLab.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Integer> {

    List<CartItem> findByUser(User user);

    Optional<CartItem> findByUserAndProduct(User user, Product product);
}