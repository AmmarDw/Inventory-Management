package com.speedit.inventorysystem.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int optionId;

    @NotBlank(message = "Option value must not be blank")
    private String optionValue;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id")
    private OptionCategory category;

    @ManyToMany(mappedBy = "productOptions")
    private List<Product> products;
}