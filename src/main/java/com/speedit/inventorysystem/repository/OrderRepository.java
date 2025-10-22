package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {
}
