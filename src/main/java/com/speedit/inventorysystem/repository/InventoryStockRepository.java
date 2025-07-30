package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.model.Inventory;
import com.speedit.inventorysystem.model.InventoryStock;
import com.speedit.inventorysystem.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, Integer> {
    Optional<InventoryStock> findByInventoryAndProductAndOrderItemIsNull(Inventory inv, Product p);
}
