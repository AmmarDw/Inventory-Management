package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.dto.OrderDTO;
import com.speedit.inventorysystem.dto.ProductStockDTO;
import com.speedit.inventorysystem.model.Inventory;
import com.speedit.inventorysystem.model.InventoryStock;
import com.speedit.inventorysystem.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, Integer> {
    Optional<InventoryStock> findByInventoryAndProductAndOrderItemIsNull(Inventory inv, Product p);

    @Query("SELECT NEW com.speedit.inventorysystem.dto.OrderDTO(" +
            "o.orderId, c.name, o.deliveryLocation) " +
            "FROM InventoryStock s " +
            "JOIN s.orderItem oi " +
            "JOIN oi.order o " +
            "JOIN o.client c " +
            "WHERE s.inventory.inventoryId = :inventoryId " +
            "AND s.orderItem IS NOT NULL " +
            "GROUP BY o.orderId, c.name, o.deliveryLocation")
    List<OrderDTO> findOrdersByInventory(@Param("inventoryId") Integer inventoryId);

    @Query("SELECT NEW com.speedit.inventorysystem.dto.ProductStockDTO(" +
            "p.productId, p, SUM(s.amount)) " +
            "FROM InventoryStock s " +
            "JOIN s.product p " +
            "LEFT JOIN s.orderItem oi " +  // Changed to LEFT JOIN
            "WHERE s.inventory.inventoryId = :inventoryId " +
            "AND ( (:orderId IS NULL AND oi IS NULL) " + // Available stock
            "OR (:orderId IS NOT NULL AND oi.order.orderId = :orderId) ) " + // Order-bound
            "GROUP BY p.productId, p")
    List<ProductStockDTO> findProductsByInventoryAndOrder(
            @Param("inventoryId") Integer inventoryId,
            @Param("orderId") Integer orderId);

    // For available stock
    @Query("SELECT s FROM InventoryStock s " +
            "WHERE s.inventory.inventoryId = :inventoryId " +
            "AND s.product.productId = :productId " +
            "AND s.orderItem IS NULL")
    Optional<InventoryStock> findByInventoryInventoryIdAndProductProductIdAndOrderItemIsNull(
            @Param("inventoryId") Integer inventoryId,
            @Param("productId") Integer productId);

    // For order-bound stock
    @Query("SELECT s FROM InventoryStock s " +
            "WHERE s.inventory.inventoryId = :inventoryId " +
            "AND s.product.productId = :productId " +
            "AND s.orderItem.order.orderId = :orderId")
    Optional<InventoryStock> findByInventoryInventoryIdAndProductProductIdAndOrderItemOrderOrderId(
            @Param("inventoryId") Integer inventoryId,
            @Param("productId") Integer productId,
            @Param("orderId") Integer orderId);
}
