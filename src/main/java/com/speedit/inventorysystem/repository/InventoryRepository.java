package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Integer> {
}
