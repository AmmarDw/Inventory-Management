package com.speedit.inventorysystem.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
    private List<ProductOption> productOptions = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryStock> inventoryStocks = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    public String getProductOptionsDisplay() {
        if (productOptions == null || productOptions.isEmpty()) return "";
        return productOptions.stream()
                .map(ProductOption::getOptionValue)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}

