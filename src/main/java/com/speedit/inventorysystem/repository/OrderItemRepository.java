package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {
}
