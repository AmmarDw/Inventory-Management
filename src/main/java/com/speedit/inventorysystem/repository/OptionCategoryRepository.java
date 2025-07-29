package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.model.OptionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OptionCategoryRepository extends JpaRepository<OptionCategory, Integer> {
    Optional<OptionCategory> findByCategoryName(String name);
}
