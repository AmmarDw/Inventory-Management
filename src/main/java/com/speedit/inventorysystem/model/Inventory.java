package com.speedit.inventorysystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.speedit.inventorysystem.enums.InventoryTypeEnum;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private Integer inventoryId;

    @NotNull(message = "Please select a type")
    @Enumerated(EnumType.STRING)
    private InventoryTypeEnum inventoryType;

    /**
     * Stores a human-readable description of the location (e.g., "Main Warehouse, King Fahd Dist.")
     * This description can be reverse-geocoded from the coordinates.
     */
    @NotBlank(message = "Please enter a location description")
    private String location;

    /**
     * The latitude coordinate. Essential for all routing calculations.
     */
    @Column(precision = 10, scale = 6)
    private BigDecimal latitude;

    /**
     * The longitude coordinate. Essential for all routing calculations.
     */
    @Column(precision = 10, scale = 6)
    private BigDecimal longitude;

    private boolean status; // true = operating (default), false = out of service

    // In cubic cm
    @Column(name = "capacity", precision = 15, scale = 2)
    @NotNull
    @Positive(message = "Capacity must be non-negative")
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal capacity;

    @OneToMany(mappedBy = "inventory", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<InventoryStock> inventoryStocks = new ArrayList<>();
}