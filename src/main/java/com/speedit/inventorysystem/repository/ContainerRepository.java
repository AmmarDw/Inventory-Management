package com.speedit.inventorysystem.repository;

import com.speedit.inventorysystem.enums.UnitType;
import com.speedit.inventorysystem.model.Container;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable; // Import Pageable
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List; // Keep for non-paginated version if needed
import java.util.Optional;

@Repository
public interface ContainerRepository extends JpaRepository<Container, Integer> {

    /**
     * Finds a page of containers, eagerly fetching the parent product and the child product.
     * Supports pagination.
     *
     * @param pageable Pagination information.
     * @return Page of Container entities with parentProduct and childProduct loaded.
     */
    @Query("SELECT c FROM Container c " +
            "JOIN FETCH c.parentProduct " +
            "JOIN FETCH c.childProduct ")
    Page<Container> findAllWithParentAndChild(Pageable pageable); // Updated signature

    /**
     * Finds a specific container by its parent product ID, eagerly fetching related entities.
     * Useful for checking if a product is a parent container or building hierarchy.
     * @param parentProductId The ID of the parent product.
     * @return Optional containing the Container or empty if not found.
     */
    @Query("SELECT c FROM Container c " +
            "JOIN FETCH c.parentProduct " +
            "JOIN FETCH c.childProduct " +
            "WHERE c.parentProduct.productId = :parentProductId")
    Optional<Container> findByParentProductId(@Param("parentProductId") Integer parentProductId);

    Optional<Container> findByParentProduct_ProductId(Integer parentProductId);

    /**
     * Finds a page of containers based on container filters and keyset pagination.
     * For 'NEXT' page: finds containers with ID > :afterId, ordered by ID ASC.
     * For 'PREVIOUS' page: finds containers with ID < :beforeId, ordered by ID DESC.
     * The service layer will reverse the order if needed for 'PREVIOUS'.
     *
     * @param minQuantity  Minimum quantity (inclusive), can be null.
     * @param maxQuantity  Maximum quantity (inclusive), can be null.
     * @param unit         Specific unit type, can be null.
     * @param minPrice
     * @param maxPrice
     * @param minVolume
     * @param maxVolume
     * @param afterId      The ID to start fetching *after* (for NEXT page).
     * @param beforeId     The ID to start fetching *before* (for PREVIOUS page).
     * @param pageable not limit    The maximum number of results to fetch.
     * @return A List of Container entities matching the criteria.
     */
    @Query("SELECT c FROM Container c " +
            "JOIN FETCH c.parentProduct " +
            "JOIN FETCH c.childProduct " +
            "WHERE (:minQuantity IS NULL OR c.quantity >= :minQuantity) " +
            "AND (:maxQuantity IS NULL OR c.quantity <= :maxQuantity) " +
            "AND (:unit IS NULL OR c.unit = :unit) " +
            // filters for the parent product
            "AND (:minPrice IS NULL OR c.parentProduct.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR c.parentProduct.price <= :maxPrice) " +
            "AND (:minVolume IS NULL OR c.parentProduct.volume >= :minVolume) " +
            "AND (:maxVolume IS NULL OR c.parentProduct.volume <= :maxVolume) " +
            // Keyset pagination logic
            "AND (COALESCE(:afterId, -1) = -1 OR c.containerId > :afterId) " +
            "AND (COALESCE(:beforeId, -1) = -1 OR c.containerId < :beforeId) " +
            "ORDER BY c.containerId ASC")
    List<Container> findByContainerFiltersKeyset(
            @Param("minQuantity") Integer minQuantity,
            @Param("maxQuantity") Integer maxQuantity,
            @Param("unit") UnitType unit,
            // ✨ NEW parameters
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            @Param("minVolume") BigDecimal minVolume,
            @Param("maxVolume") BigDecimal maxVolume,
            // Keyset pagination parameters
            @Param("afterId") Integer afterId,
            @Param("beforeId") Integer beforeId,
            Pageable pageable);

    List<Container> findByChildProduct_ProductId(Integer id);
}