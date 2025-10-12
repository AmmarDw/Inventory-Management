package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.ArrayList;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    /**
     * Finds products based on a combination of price range, minimum stock, selected options,
     * and excludes products that are parents of containers.
     * Uses a native query with explicit table and column names.
     * Fixed the option filtering clause to correctly handle list binding and null/empty checks.
     *
     * @param minPrice    Minimum price (inclusive), can be null to ignore.
     * @param maxPrice    Maximum price (inclusive), can be null to ignore.
     * @param minStock    Minimum stock level, can be null to ignore.
     * @param optionIds   List of option IDs that the product must have, can be null/empty to ignore.
     *                     MUST be an empty list (NOT null) if no options are selected.
     * @param optionCount The number of option IDs provided. Used to skip logic if 0.
     * @return List of products matching the criteria (excluding container parents).
     */
    @Query(value =
            "SELECT DISTINCT p.* " +
                    "FROM product p " +
                    // --- Add LEFT JOIN to check for container association ---
                    "LEFT JOIN container c_check ON p.product_id = c_check.parent_product_id " +
                    //----------------------------------------------------------
                    "WHERE " +
                    // --- Add condition to exclude container parents ---
                    "  c_check.container_id IS NULL AND " +
                    //-------------------------------------------------
                    "  (?1 IS NULL OR p.price >= ?1) " +
                    "  AND (?2 IS NULL OR p.price <= ?2) " +
                    "  AND (?3 IS NULL OR (SELECT COALESCE(SUM(invstk.amount), 0) FROM inventory_stock invstk WHERE invstk.product_id = p.product_id) >= ?3) " +
                    // --- Revised Option Filtering Clause ---
                    // Check optionCount. If it's 0, skip the option filtering entirely.
                    // Assume optionIds is passed as an empty list (not null) by the default method.
                    "  AND (?5 = 0 OR " +
                    "       p.product_id IN (SELECT p2.product_id FROM product p2 " +
                    "                        JOIN product_option_mapping pom ON p2.product_id = pom.product_id " +
                    "                        JOIN product_option po2 ON pom.option_id = po2.option_id " +
                    "                        WHERE po2.option_id IN (?4) " + // ?4 is the List<Integer> optionIds
                    "                        GROUP BY p2.product_id " +
                    "                        HAVING COUNT(DISTINCT po2.option_id) = ?5)" + // ?5 is Long optionCount
                    "      )",
            nativeQuery = true
    )
    List<Product> findByFilters(
            Long minPrice,
            Long maxPrice,
            Integer minStock,
            List<Integer> optionIds, // This will be an empty list, not null, due to default method
            Long optionCount
    );

    // Default method ensures optionIds is NEVER null and optionCount is correct.
    default List<Product> findByFilters(Long minPrice, Long maxPrice, Integer minStock, List<Integer> optionIds) {
        // Ensure optionIds is never null. Pass an empty list if null.
        // This makes the native query parameter binding more predictable.
        List<Integer> safeOptionIds = (optionIds != null) ? optionIds : new ArrayList<>();
        Long optionCount = (long) safeOptionIds.size();
        // Pass the (potentially empty, but not null) list and its count
        return findByFilters(minPrice, maxPrice, minStock, safeOptionIds, optionCount);
    }

    /**
     * Finds a page of base products (products that are not parent containers).
     * Explicitly selects only Product fields to avoid triggering EAGER fetch
     * of the 'container' association.
     *
     * @param pageable Pagination information.
     * @return A Page of base Product entities.
     */
    @Query("SELECT p FROM Product p WHERE p.container IS NULL")
    Page<Product> findBaseProductsWithPagination(Pageable pageable);

}