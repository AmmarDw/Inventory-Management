package com.speedit.inventorysystem.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private Integer inventoryStockId;

    @ManyToOne
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @ManyToOne @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** null = available stock; non‑null = reserved for a specific OrderItem */
    @ManyToOne @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @NotNull
    @PositiveOrZero
    private Integer amount;
}

