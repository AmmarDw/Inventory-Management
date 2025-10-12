package com.speedit.inventorysystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private Integer productId;

    /** only the checksum portion of barcode */
    @PositiveOrZero
    private Integer barcodeChecksum;

    @NotNull
    @Positive
    private Long price;

    @ManyToMany
    @JoinTable(name = "product_option_mapping",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "option_id"))
    @JsonIgnoreProperties({"products", "category"})
    private List<ProductOption> productOptions = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<InventoryStock> inventoryStocks = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<OrderItem> orderItems = new ArrayList<>();
//issue salem
    @OneToOne(mappedBy = "parentProduct", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JsonIgnore
    private Container container; // This links a Product (if it's a container parent) to its Container definition.

    // In cubic cm
    @Column(name = "volume", precision = 10, scale = 5) // Increased scale for more precision if needed
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false) // Must be strictly positive
    private BigDecimal volume; // Using BigDecimal for precision

    // In cm
    @Column(precision = 8, scale = 5)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false) 
    private BigDecimal height;

    // In cm
    @Column(precision = 8, scale = 5)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal width;

    // In cm
    @Column(precision = 8, scale = 5)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal length;

    public String getProductOptionsDisplay() {
        if (productOptions == null || productOptions.isEmpty()) return "";
        return productOptions.stream()
                .map(ProductOption::getOptionValue)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}

