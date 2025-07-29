package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.model.OptionCategory;
import com.speedit.inventorysystem.model.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Integer> {
    Optional<ProductOption> findByOptionValueAndCategory(String optionValue, OptionCategory category);
}
