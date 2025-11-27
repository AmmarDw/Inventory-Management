package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.enums.InventoryTypeEnum;
import com.speedit.inventorysystem.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Integer> {
    @Query("""
        SELECT inv FROM Inventory inv
        WHERE inv.inventoryType = :type
          AND inv.status = true
    """)
    List<Inventory> findActiveByType(@Param("type") InventoryTypeEnum type);
}
