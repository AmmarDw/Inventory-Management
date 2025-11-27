package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.enums.MovementStatus;
import com.speedit.inventorysystem.model.Inventory;
import com.speedit.inventorysystem.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /**
     * Latest DONE movement where the inventory participated as from or to.
     */
    @Query("""
       SELECT m FROM StockMovement m
       WHERE (m.fromInventory = :inventory OR m.toInventory = :inventory)
         AND m.movementStatus = :status
       ORDER BY m.moveAt DESC
    """)
    List<StockMovement> findLatestByInventoryAndStatus(
            @Param("inventory") Inventory inventory,
            @Param("status") MovementStatus status
    );

    /**
     * Future scheduled movements for this inventory ordered by moveAt ASC.
     */
    @Query("""
       SELECT m FROM StockMovement m
       WHERE (m.fromInventory = :inventory OR m.toInventory = :inventory)
         AND m.moveAt > :after
       ORDER BY m.moveAt ASC
    """)
    List<StockMovement> findFutureByInventory(
            @Param("inventory") Inventory inventory,
            @Param("after") OffsetDateTime after
    );
}
