package com.speedit.inventorysystem.model;

import com.speedit.inventorysystem.enums.UnitType;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;

/**
 * Represents a packaging definition for products.
 * A container defines how many units of a child product make up a specific parent product.
 * E.g., defines that 1 "Pack of 12 Chocolate Bars" (parentProduct) contains 12 "Chocolate Bar" (childProduct).
 * The parentProduct is mandatory and unique to this container definition.
 */
@Entity
@Table(name = "container") // Explicitly specify table name
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Container extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private Integer containerId;

    /**
     * The unique parent Product this container defines (e.g., the "Pack of 12" product record).
     * This establishes a strict 1:1 relationship between the Container definition and its Product representation.
     * The relationship is mandatory; a Container must define its specific packaging Product.
     */
    @OneToOne(optional = false, fetch = FetchType.LAZY, cascade = CascadeType.REMOVE) // (0/1) to 1 from Container's perspective, but mandatory (optional=false) for the link to exist
    @JoinColumn(name = "parent_product_id", nullable = false, unique = true) // unique=true enforces the 1:1 nature on the DB level
    @NotNull // Bean Validation
    private Product parentProduct;

    /**
     * The child Product or base Product contained within (e.g., the individual "Chocolate Bar").
     * Many containers can reference the same child product.
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY) // A container must hold something
    @JoinColumn(name = "child_product_id", nullable = false)
    @NotNull
    private Product childProduct;

    /**
     * The number of child product units contained within one unit of the parent product.
     */
    @NotNull
    @Positive
    private Integer quantity;

    /**
     * The unit of measure for the quantity (e.g., "bars", "packs", "units").
     */
    @Enumerated(EnumType.STRING) // Store enum value as string in DB
    @Column(name = "unit", nullable = false)
    @NotNull
    private UnitType unit;

}