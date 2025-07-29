package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Integer> {
}
