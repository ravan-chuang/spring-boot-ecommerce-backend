package com.ravan.SpringBootLab.repository;

import com.ravan.SpringBootLab.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Integer> {
}