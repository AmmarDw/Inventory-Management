package com.speedit.inventorysystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.speedit.inventorysystem.enums.InventoryTypeEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

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

    @NotBlank(message = "Please enter a location")
    private String location;

    private boolean status; // true = operating (default), false = out of service

    @OneToMany(mappedBy = "inventory", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<InventoryStock> inventoryStocks = new ArrayList<>();
}