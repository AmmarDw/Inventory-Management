package com.speedit.inventorysystem.model;

import com.speedit.inventorysystem.enums.MovementStatus;
import com.speedit.inventorysystem.enums.MovementType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "stock_movement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private Long movementId;

    /**
     * The stock this movement is about.
     * In Phase A we create these in memory only; in Phase B we persist them.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "inventory_stock_id", nullable = false)
    private InventoryStock inventoryStock;

    /**
     * From which inventory the stock is moved.
     * For VAN -> CLIENT, this is the van.
     */
    @ManyToOne
    @JoinColumn(name = "from_inventory_id")
    private Inventory fromInventory;

    /**
     * To which inventory the stock is moved.
     * Null means "to customer/client".
     */
    @ManyToOne
    @JoinColumn(name = "to_inventory_id")
    private Inventory toInventory;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_status", nullable = false)
    private MovementStatus movementStatus;

    /**
     * When this movement is expected to happen.
     * In Phase A we compute a rough planned time (respecting working hours).
     */
    @Column(name = "move_at", nullable = false)
    private OffsetDateTime moveAt;

    /**
     * Cached total volume of this movement (cc).
     * Optional, but useful for faster capacity calculations.
     */
    @Column(name = "estimated_volume_cc", precision = 18, scale = 2)
    private BigDecimal estimatedVolumeCc;

    /**
     * Who is responsible for this movement (driver / staff).
     * Optional in Phase A (we can leave it null).
     */
    @ManyToOne
    @JoinColumn(name = "employee_id")
    private User assignedEmployee;
}
