// ContainerService.java
package com.speedit.inventorysystem.service;

import com.speedit.inventorysystem.dto.*;
import com.speedit.inventorysystem.enums.MeasurementUnitEnum;
import com.speedit.inventorysystem.enums.UnitType;
import com.speedit.inventorysystem.model.Container;
import com.speedit.inventorysystem.model.Product;
import com.speedit.inventorysystem.model.ProductOption;
import com.speedit.inventorysystem.repository.ContainerRepository;
import com.speedit.inventorysystem.repository.InventoryStockRepository;
import com.speedit.inventorysystem.repository.ProductRepository; // Import ProductRepository
import com.speedit.inventorysystem.service.ProductService;
import jakarta.persistence.EntityNotFoundException; // Import for specific exception
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException; // Import for constraint handling
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Import for transactionality

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ContainerService {

    @Autowired
    private ContainerRepository containerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    @Autowired
    private ProductService productService;

    // Autowire barcode prefixes if needed for ProductDTO conversion
    @Value("${barcode.prefix.country:}")
    private String countryPrefix;

    @Value("${barcode.prefix.company:}")
    private String companyPrefix;



    /**
     * Creates a new container. This involves creating a new Product to represent
     * the container itself, and then creating the Container entity linking it
     * to the specified child product.
     *
     * @param request The container creation request data.
     * @return The created Container entity.
     * @throws EntityNotFoundException if the child product is not found.
     * @throws DataIntegrityViolationException if constraints are violated.
     */
    @Transactional // Ensures creation of Product and Container are atomic
    public Container createContainer(ContainerRequestDTO request) { // Use DTO
        // 1. Validate child product exists
        Product childProduct = productRepository.findById(request.getChildProductId())
                .orElseThrow(() -> new EntityNotFoundException("Child product with ID " + request.getChildProductId() + " not found."));

        // 2. Calculate dimensions and volume before creating the product
        MeasurementUnitEnum unit = MeasurementUnitEnum.valueOf(request.getDistanceUnit().toUpperCase());
        BigDecimal factor = unit.getToBaseFactor();

        BigDecimal heightInCm = request.getHeight().multiply(factor);
        BigDecimal widthInCm = request.getWidth().multiply(factor);
        BigDecimal lengthInCm = request.getLength().multiply(factor);
        BigDecimal volumeInCm3 = heightInCm.multiply(widthInCm).multiply(lengthInCm);

        // 3. Create the new Parent Product using the updated ProductService method signature
        Product parentProduct = new Product();
        parentProduct.setPrice(request.getParentProductPrice());
        parentProduct.setProductOptions(java.util.Collections.emptyList());
        parentProduct.setHeight(heightInCm);
        parentProduct.setWidth(widthInCm);
        parentProduct.setLength(lengthInCm);
        parentProduct.setVolume(volumeInCm3);

        parentProduct = productService.createProduct(parentProduct);

        // 4. Create the Container entity
        Container container = new Container();
        container.setParentProduct(parentProduct);
        container.setChildProduct(childProduct);
        container.setQuantity(request.getQuantity());
        container.setUnit(request.getUnit());

        // 5. Save and link entities
        container = containerRepository.save(container);
        parentProduct.setContainer(container);
        productRepository.save(parentProduct);

        return container;
    }

    /**
     * Retrieves a page of containers and builds a map containing the hierarchical "strain"
     * for each container on that page. This supports future pagination.
     * Also prepares common form data needed for the UI.
     * Uses eager fetching configured in the Container entity to minimize queries.
     *
     * @param pageable Pagination information (page number, size, sort).
     * @return A map containing "containersPage" (Page<Container> - for pagination metadata)
     *         and "containersMap" (Map<Integer, Map<String, Object>> - strain data for the page).
     */
    public Map<String, Object> getContainersPageWithStrain(Pageable pageable) {
        // 1. Fetch the requested page of containers
        Page<Container> containersPage = containerRepository.findAllWithParentAndChild(pageable);

        // 2. Prepare the map to hold hierarchical data for containers ON THIS PAGE
        Map<Integer, Map<String, Object>> containersMap = new HashMap<>();

        // 3. Iterate through each container ON THIS PAGE to build its strain
        for (Container container : containersPage.getContent()) {
            Integer parentProductId = container.getParentProduct().getProductId();
            // Build the strain map for this specific container using eager-loaded data
            Map<String, Object> strainData = buildContainerStrain(container);
            containersMap.put(parentProductId, strainData);
        }

        // 4. Return the page (for pagination controls) and the strain data map
        return Map.of(
                "containersPage", containersPage,
                "containersMap", containersMap
        );
    }

    /**
     * Recursively builds the hierarchical data ("strain") for a given starting container.
     * Fetches related containers from the database as needed.
     * This method is suitable for use with paginated data.
     *
     * @param startingContainer The container to start building the strain from.
     * @return A Map representing the strain: { levels: [...], finalProductOptions: "..." }
     */
    private Map<String, Object> buildContainerStrain(Container startingContainer) {
        Map<String, Object> strainData = new HashMap<>();
        List<Map<String, Object>> levels = new ArrayList<>();

        Container currentContainer = startingContainer; // Start with the given container
        boolean reachedEnd = false;

        // Traverse the hierarchy by querying the database for the next container
        while (!reachedEnd) {
            // Add current level details
            Map<String, Object> levelInfo = new HashMap<>();
            levelInfo.put("unit", currentContainer.getUnit().toString()); // Assuming UnitType enum
            levelInfo.put("quantity", currentContainer.getQuantity());
            levelInfo.put("childProductId", currentContainer.getChildProduct().getProductId());
            levelInfo.put("parentProductId", currentContainer.getParentProduct().getProductId());
            levels.add(levelInfo);

            Container nextContainer = currentContainer.getChildProduct().getContainer();

            if (nextContainer != null) {
                // The child is a parent of another container, continue traversing
                currentContainer = nextContainer;
            } else {
                // Reached the end of the chain, the child is a base product
                try {
                    // Fetch the final product's options display string
                    Product finalProduct = currentContainer.getChildProduct();
                    String optionsDisplay = finalProduct.getProductOptionsDisplay();

                    strainData.put("finalProductOptions", optionsDisplay != null ? optionsDisplay : "N/A");
                    strainData.put("finalProductId", finalProduct.getProductId());
                    // Add price of the top-level parent container (the one we started with)
                    strainData.put("parentProductPrice", startingContainer.getParentProduct().getPrice());

                } catch (Exception e) {
                    System.err.println("Error processing final product ID: " + currentContainer.getChildProduct().getProductId() + ". Error: " + e.getMessage());
                    strainData.put("finalProductOptions", "Error loading options");
                    strainData.put("finalProductId", currentContainer.getChildProduct().getProductId());
                    strainData.put("parentProductPrice", startingContainer.getParentProduct().getPrice());
                }
                // Break the loop as we've reached the base product
                reachedEnd = true;
            }
        }

        strainData.put("levels", levels);
        return strainData;
    }


    // Keep utility methods like isParentContainer if needed elsewhere
    public boolean isParentContainer(Integer productId) {
        return containerRepository.findByParentProductId(productId).isPresent();
    }


    /**
     * Fetches ProductSummaryDTOs for the child product dropdown.
     * Combines base products and container parent products.
     * Implements a "double page size" fetch strategy to gather more data
     * before applying the search filter, aiming to meet the requested page size.
     *
     * @param searchTerm Optional search term to filter by ID or display text.
     * @param pageable   Original pagination request (page, size, sort).
     * @return A Page of ProductSummaryDTO objects, potentially exceeding the original size if enough matches are found quickly.
     */
    public Map<String, Object> getProductSummariesForDropdown(String searchTerm, Pageable pageable) {
        final int originalPageSize = pageable.getPageSize();
        final int originalPageNumber = pageable.getPageNumber();
        final Sort sort = pageable.getSort();

        List<ProductSummaryDTO> finalFilteredResults = new ArrayList<>();
        int currentFetchPageNumber = originalPageNumber;
        boolean hasMoreBaseProducts = true;
        boolean hasMoreContainers = true;
        long totalElementsEstimate = 0; // Will be refined

        // Loop until we have enough results or run out of data
        while (finalFilteredResults.size() < originalPageSize && (hasMoreBaseProducts || hasMoreContainers)) {
            List<ProductSummaryDTO> batchToFilter = new ArrayList<>();

            // --- Fetch Base Products for current fetch page ---
            if (hasMoreBaseProducts) {
                try {
                    Pageable fetchPageable = PageRequest.of(currentFetchPageNumber, originalPageSize, sort);
                    // Use ProductService to get paginated base products
                    Page<Product> baseProductPage = productService.getAllProducts(fetchPageable);

                    // Transform Product entities to ProductSummaryDTOs
                    List<ProductSummaryDTO> baseProductSummaries = baseProductPage.getContent().stream()
                            .map(product -> new ProductSummaryDTO(
                                    product.getProductId(),
                                    product.getProductId() + " - " + product.getProductOptionsDisplay() // Use existing method
                            ))
                            .collect(Collectors.toList());

                    batchToFilter.addAll(baseProductSummaries);

                    // Update state: check if more base products exist
                    if (!baseProductPage.hasNext()) {
                        hasMoreBaseProducts = false;
                        totalElementsEstimate += baseProductPage.getTotalElements();
                    } else {
                        totalElementsEstimate += originalPageSize; // Estimate
                    }

                } catch (Exception e) {
                    System.err.println("Error fetching base products for dropdown (page " + currentFetchPageNumber + "): " + e.getMessage());
                    e.printStackTrace();
                    hasMoreBaseProducts = false; // Stop fetching base products on error
                }
            }

            // --- Fetch Containers for current fetch page ---
            if (hasMoreContainers) {
                try {
                    Sort sortContainer = Sort.by(Sort.Direction.ASC, "parentProduct");
                    Pageable fetchPageable = PageRequest.of(currentFetchPageNumber, originalPageSize, sortContainer);
                    // Use the existing method, but we only need the Page<Container> and containersMap
                    Map<String, Object> containerDataMap = this.getContainersPageWithStrain(fetchPageable);
                    Page<Container> containerPage = (Page<Container>) containerDataMap.get("containersPage");

                    // Process containers to build summaries using the strain data provided by getContainersPageWithStrain
                    Map<Integer, Map<String, Object>> containersMap = (Map<Integer, Map<String, Object>>) containerDataMap.get("containersMap");

                    List<ProductSummaryDTO> containerSummaries = containerPage.getContent().stream()
                            .map(container -> {
                                Integer parentProductId = container.getParentProduct().getProductId();
                                Map<String, Object> strainData = containersMap.get(parentProductId);

                                if (strainData != null) {
                                    try {
                                        StringBuilder displayTextBuilder = new StringBuilder();
                                        displayTextBuilder.append(parentProductId).append(" - ");

                                        // Build display text from pre-fetched strain data
                                        List<Map<String, Object>> levels = (List<Map<String, Object>>) strainData.get("levels");
                                        if (levels != null) {
                                            for (int i = 0; i < levels.size(); i++) {
                                                Map<String, Object> level = levels.get(i);
                                                displayTextBuilder.append(level.get("unit")).append(" contains ").append(level.get("quantity"));
                                                displayTextBuilder.append(" -> \n");
                                            }
                                        }
                                        // Add final product options
                                        String finalOptions = (String) strainData.get("finalProductOptions");
                                        if (finalOptions != null && !finalOptions.isEmpty()) {
                                            displayTextBuilder.append(finalOptions);
                                        }

                                        return new ProductSummaryDTO(parentProductId, displayTextBuilder.toString());
                                    } catch (Exception e) {
                                        System.err.println("Error processing container strain data for dropdown (Parent ID: " + parentProductId + "): " + e.getMessage());
                                        return new ProductSummaryDTO(parentProductId, parentProductId + " - Error building container hierarchy");
                                    }
                                } else {
                                    System.err.println("Strain data missing for container parent product ID: " + parentProductId);
                                    return new ProductSummaryDTO(parentProductId, parentProductId + " - Container (Hierarchy data unavailable)");
                                }
                            })
                            .collect(Collectors.toList());

                    batchToFilter.addAll(containerSummaries);

                    // Update state: check if more containers exist
                    if (!containerPage.hasNext()) {
                        hasMoreContainers = false;
                        totalElementsEstimate += containerPage.getTotalElements();
                    } else {
                        totalElementsEstimate += originalPageSize; // Estimate
                    }

                } catch (Exception e) {
                    System.err.println("Error fetching containers for dropdown (page " + currentFetchPageNumber + "): " + e.getMessage());
                    e.printStackTrace();
                    hasMoreContainers = false; // Stop fetching containers on error
                }
            }

            // --- Apply Search Filter to the Current Batch ---
            List<ProductSummaryDTO> filteredBatch;
            if (searchTerm != null && !searchTerm.isEmpty()) {
                final String lowerSearchTerm = searchTerm.toLowerCase().trim();
                filteredBatch = batchToFilter.stream()
                        .filter(dto -> {
                            String idStr = String.valueOf(dto.getProductId());
                            String displayText = dto.getDisplayText().toLowerCase();
                            return idStr.contains(lowerSearchTerm) || displayText.contains(lowerSearchTerm);
                        })
                        .collect(Collectors.toList());
            } else {
                // If no search term, the whole batch is considered matching
                filteredBatch = batchToFilter;
            }

            // --- Add Filtered Results to Final List ---
            finalFilteredResults.addAll(filteredBatch);

            // --- Move to the Next Fetch Page ---
            currentFetchPageNumber++;
        }

        // --- Estimate Total Pages ---
        int estimatedTotalPages = (int) Math.ceil((double) totalElementsEstimate / originalPageSize);
        if (estimatedTotalPages <= 0 && !finalFilteredResults.isEmpty()) {
            estimatedTotalPages = 1;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content", finalFilteredResults);
        response.put("page", originalPageNumber);
        response.put("size", originalPageSize);
        response.put("totalElements", finalFilteredResults.size());
        response.put("totalPages", estimatedTotalPages);
        response.put("hasMore", hasMoreBaseProducts || hasMoreContainers);

        return response;
        // Or potentially return totalElementsEstimate if you prefer it for pagination controls,
        // understanding it's an estimate: return new PageImpl<>(finalFilteredResults, originalPageableForResponse, totalElementsEstimate);
    }

    /**
     * Updates an existing container and its associated parent product.
     *
     * @param parentProductId The ID of the container to update.
     * @param request     The request object containing updated data.
     * @return The updated Container entity.
     * @throws EntityNotFoundException       If the container or specified child product is not found.
     * @throws DataIntegrityViolationException If updating violates database constraints (e.g., uniqueness).
     */
    @Transactional // Ensures update operations are atomic
    public Container updateContainer(Integer parentProductId, ContainerRequestDTO request) {
        // 1. Find the existing container and its parent product
        Container existingContainer = containerRepository.findByParentProduct_ProductId(parentProductId)
                .orElseThrow(() -> new EntityNotFoundException("Container with parent product ID " + parentProductId + " not found."));
        Product parentProduct = existingContainer.getParentProduct();

        // 2. Find the new child product (if different)
        Product newChildProduct = productRepository.findById(request.getChildProductId())
                .orElseThrow(() -> new EntityNotFoundException("Child product with ID " + request.getChildProductId() + " not found."));

        // 3. Use the helper method to update the parent product's properties
        updateParentProductFromRequest(parentProduct, request);

        // 4. Update the container's direct fields
        existingContainer.setChildProduct(newChildProduct);
        existingContainer.setQuantity(request.getQuantity());
        existingContainer.setUnit(request.getUnit());

        // 5. Save the updated entities
        productRepository.save(parentProduct);
        return containerRepository.save(existingContainer);
    }

    /**
     * Fetches data required to populate the Container Edit form.
     * This includes the container's core data (as ContainerRequestDTO)
     * and a summary of its child product (as ProductSummaryDTO).
     *
     * @param parentProductId The ID of the parent product identifying the container.
     * @return A Map containing "containerData" (ContainerRequestDTO) and "childProductSummary" (ProductSummaryDTO),
     *         or an empty map if not found.
     * @throws EntityNotFoundException if the container is not found.
     */
    public Map<String, Object> getContainerEditData(Integer parentProductId) throws EntityNotFoundException {
        // 1. Find the Container entity by its parent product ID
        Optional<Container> containerOpt = containerRepository.findByParentProduct_ProductId(parentProductId);

        if (containerOpt.isPresent()) {
            Container container = containerOpt.get();
            Product parentProduct = container.getParentProduct();
            Map<String, Object> result = new HashMap<>();

            // 2. Map Container entity to ContainerRequestDTO (for form population)
            ContainerRequestDTO containerData = new ContainerRequestDTO();
            containerData.setParentProductPrice(parentProduct.getPrice());
            containerData.setChildProductId(container.getChildProduct().getProductId());
            containerData.setQuantity(container.getQuantity());
            containerData.setUnit(container.getUnit());
            containerData.setHeight(parentProduct.getHeight());
            containerData.setWidth(parentProduct.getWidth());
            containerData.setLength(parentProduct.getLength());
            // Note: ContainerRequestDTO might not have parentProductId directly,
            // as it's the ID used to find the container. The ID is the path variable {id}.

            // 3. Build ProductSummaryDTO for the child product
            ProductSummaryDTO childProductSummary = buildProductSummaryDTO(container.getChildProduct());

            // 4. Package the DTOs into the result map
            result.put("containerData", containerData);
            result.put("childProductSummary", childProductSummary);

            return result;
        } else {
            throw new EntityNotFoundException("Container with parent product ID " + parentProductId + " not found.");
            // Returning an empty map is also an option, but throwing ENFE allows the controller to return 404.
            // return new HashMap<>();
        }
    }

    /**
     * Builds a ProductSummaryDTO for a single Product entity.
     * Handles both base products and container parent products.
     *
     * @param product The Product entity.
     * @return A populated ProductSummaryDTO.
     */
    private ProductSummaryDTO buildProductSummaryDTO(Product product) {
        if (product == null) {
            return new ProductSummaryDTO(null, "N/A");
        }

        // Check if the product is itself a parent container
        Container container = product.getContainer(); // This relies on EAGER fetch or being loaded

        if (container != null) {
            // It's a container parent product. Build its hierarchy string.
            // We can reuse logic or fetch strain data, but for a single item,
            // let's build it directly. This assumes eager loading is sufficient
            // or data is already loaded correctly.
            try {
                // Simplified hierarchy building (similar to strain logic but for one item)
                StringBuilder displayText = new StringBuilder();
                displayText.append(product.getProductId()).append(" - ");

                Container current = container;
                while (current != null) {
                    displayText.append(current.getUnit()).append(" contains ").append(current.getQuantity());
                    Product childProduct = current.getChildProduct();
                    Container nextContainer = (childProduct != null) ? childProduct.getContainer() : null;
                    if (nextContainer != null) {
                        displayText.append(" -> ");
                        current = nextContainer;
                    } else {
                        // Reached the end, add final product options
                        if (childProduct != null) {
                            displayText.append(" -> ").append(" - ").append(childProduct.getProductOptionsDisplay());
                        }
                        current = null; // Break loop
                    }
                }
                return new ProductSummaryDTO(product.getProductId(), displayText.toString());
            } catch (Exception e) {
                System.err.println("Error building container hierarchy for edit summary (Product ID: " + product.getProductId() + "): " + e.getMessage());
                return new ProductSummaryDTO(product.getProductId(), product.getProductId() + " - Error building container hierarchy");
            }
        } else {
            // It's a base product.
            return new ProductSummaryDTO(
                    product.getProductId(),
                    product.getProductId() + " - " + product.getProductOptionsDisplay()
            );
        }
    }

    /**
     * Fetches detailed data for a container to display in the View modal.
     * Aggregates data from the Container entity, its parent product, and related services.
     *
     * @param parentProductId The ID of the parent product identifying the container.
     * @return A populated ContainerViewDTO.
     * @throws EntityNotFoundException if the container is not found.
     */
    public ContainerViewDTO getContainerViewData(Integer parentProductId) throws EntityNotFoundException {
        // 1. Find the Container entity by its parent product ID
        Optional<Container> containerOpt = containerRepository.findByParentProduct_ProductId(parentProductId);

        if (containerOpt.isPresent()) {
            Container container = containerOpt.get();
            Product parentProduct = container.getParentProduct();

            ContainerViewDTO dto = new ContainerViewDTO();

            // 2. Populate basic container and parent product IDs
            dto.setContainerId(container.getContainerId());
            dto.setParentProductId(parentProductId);

            // 3. Populate Hierarchy Data
            // We need to get the strain data for this specific container.
            // We can reuse the logic or fetch it. Let's assume we can get it.
            // A simple way is to call buildContainerStrain for this single container.
            // However, buildContainerStrain might rely on eager loading or DB calls.
            // Let's call it, but be aware it might trigger DB lookups.
            // Alternatively, if the strain data was fetched efficiently elsewhere and passed,
            // that would be better. For now, we'll fetch it.
            Map<String, Object> strainData = buildContainerStrain(container); // Reuse existing method
            dto.setHierarchyLevels((List<Map<String, Object>>) strainData.get("levels"));
            dto.setFinalProductOptions((String) strainData.get("finalProductOptions"));
            dto.setFinalProductId((Integer) strainData.get("finalProductId")); // If present in strainData

            // 4. Populate Product-specific fields
            dto.setVolume(parentProduct.getVolume());
            dto.setHeight(parentProduct.getHeight());
            dto.setWidth(parentProduct.getWidth());
            dto.setLength(parentProduct.getLength());
            dto.setPrice(parentProduct.getPrice()); // Price from parent product

            // 5. Calculate Total Stock (using the new sumStockByProductId method)
            // This assumes InventoryStockRepository.sumStockByProductId sums ALL stock (available + reserved)
            // for the given parent product ID.
            int totalStock = inventoryStockRepository.sumStockByProductId(parentProductId);
            dto.setTotalStock(totalStock);

            // 6. Generate Full Barcode (reuse logic, potentially from ProductService or BarcodeService)
            // Assuming ProductService or a helper method can do this.
            // You might need to adjust this based on how barcode generation is implemented.
            try {
                // Example using a potential helper or service method
                // If ProductService has a similar method or if you have a dedicated BarcodeService
                String productRef = String.format("%05d", parentProductId);
                String fullBarcode = countryPrefix + companyPrefix + productRef + parentProduct.getBarcodeChecksum();
                dto.setFullBarcode(fullBarcode);
            } catch (Exception e) {
                System.err.println("Error generating barcode for container parent product ID " + parentProductId + ": " + e.getMessage());
                dto.setFullBarcode("Error generating barcode");
            }

            // 7. Populate Audit fields (from parent product, assuming it has BaseEntity fields)
            // Make sure Product entity inherits from BaseEntity or has these fields
            dto.setCreatedAt(parentProduct.getCreatedAt()); // Assuming Product has getCreatedAt()
            dto.setCreatedBy(parentProduct.getCreatedBy()); // Assuming Product has getCreatedBy()
            dto.setUpdatedAt(parentProduct.getUpdatedAt()); // Assuming Product has getUpdatedAt()
            dto.setUpdatedBy(parentProduct.getUpdatedBy()); // Assuming Product has getUpdatedBy()

            return dto;
        } else {
            throw new EntityNotFoundException("Container with parent product ID " + parentProductId + " not found for viewing.");
        }
    }


    /**
     * Filters containers based on both container properties and properties of their final base product,
     * using keyset pagination for efficient Next/Previous navigation with filters.
     * Returns a page of containers along with their pre-built strain data and navigation flags.
     *
     * @param filterRequest The filter criteria and pagination info.
     * @return A Map containing:
     *         - "containersPage": Page<Container> (the filtered containers for the page)
     *         - "containersMap": Map<Integer, Map<String, Object>> (strain data for the containers)
     *         - "hasNext": boolean (true if there might be a next page)
     *         - "hasPrevious": boolean (true if there might be a previous page)
     */
    public Map<String, Object> filterContainersWithStrain(ContainerFilterRequest filterRequest) {
        // --- 1. Extract filter criteria ---
        Integer minQuantity = filterRequest.getMinQuantity();
        Integer maxQuantity = filterRequest.getMaxQuantity();
        UnitType unit = filterRequest.getUnit();
        BigDecimal minVolume = filterRequest.getMinVolume();
        BigDecimal maxVolume = filterRequest.getMaxVolume();

        Long minPrice = filterRequest.getMinPrice();
        Long maxPrice = filterRequest.getMaxPrice();
        Integer minStock = filterRequest.getMinStock();
        List<Integer> optionIds = filterRequest.getOptionIds();
        if (optionIds == null) optionIds = new ArrayList<>();

        boolean hasBaseProductFilters = (minPrice != null || maxPrice != null ||
                minStock != null || !optionIds.isEmpty());

        // --- 2. Extract pagination criteria ---
        String direction = filterRequest.getDirection();
        Integer lastContainerId = filterRequest.getLastContainerId();
        Integer firstContainerId = filterRequest.getFirstContainerId();
        int pageSize = filterRequest.getPageSize();
        if (pageSize <= 0) pageSize = 20; // Default fallback

        // --- 3. Determine keyset parameters for the query ---
        Integer afterId = null;
        Integer beforeId = null;
        Sort sort = Sort.by(Sort.Direction.ASC, "containerId"); // Default sort

        if ("NEXT".equalsIgnoreCase(direction) && lastContainerId != null) {
            afterId = lastContainerId;
            // sort remains ASC
        } else if ("PREVIOUS".equalsIgnoreCase(direction) && firstContainerId != null) {
            beforeId = firstContainerId;
            // For previous, we query DESC, then reverse in Java
            sort = Sort.by(Sort.Direction.DESC, "containerId");
        }
        // If neither, it's likely the first page, so afterId/beforeId stay null, sort ASC

        // --- 4. Fetch and filter loop ---
        List<Container> finalFilteredContainers = new ArrayList<>();
        // Store strain data alongside the container that passed filters
        Map<Integer, Map<String, Object>> finalContainersStrainMap = new HashMap<>(); // Key: Parent Product ID
        int totalFetched = 0; // Track total fetched to prevent infinite loops
        final int MAX_FETCHES = 100; // Safety net
        int fetchCount = 0;
        boolean hasMorePotentialData = true; // Assume there's more until proven otherwise

        while (finalFilteredContainers.size() < pageSize && hasMorePotentialData && fetchCount < MAX_FETCHES) {
            fetchCount++;
            System.out.println("Fetch iteration " + fetchCount + ", Target size: " + pageSize + ", Current size: " + finalFilteredContainers.size());

            // --- 5. Fetch a batch based on keyset and container filters ---
            Pageable fetchPageable = PageRequest.of(0, pageSize * 2, sort); // Fetch more to account for filtering
            List<Container> batchContainers = containerRepository.findByContainerFiltersKeyset(
                    minQuantity, maxQuantity, unit,
                    minPrice, maxPrice, minVolume, maxVolume,
                    afterId, beforeId, fetchPageable);

            System.out.println("Fetched " + batchContainers.size() + " containers from DB (before base product filtering).");

            // --- 6. Check if this batch could be the last ---
            if (batchContainers.size() < pageSize * 2) {
                hasMorePotentialData = false; // Likely reached the end of data matching container filters
                System.out.println("Batch size < fetch size, assuming end of potential data for container filters.");
            }

            // --- 7. Apply base product filters ---
            for (Container container : batchContainers) {
                if (finalFilteredContainers.size() >= pageSize) {
                    break; // We have enough for the page
                }

                // --- Capture Strain Data Here ---
                Map<String, Object> strainData = null;
                try {
                    strainData = buildContainerStrain(container); // Build strain data first
                } catch (Exception e) {
                    System.err.println("Error building strain for container " + container.getContainerId() + " during filtering: " + e.getMessage());
                    continue; // Skip container on strain error
                }

                boolean passesBaseFilters = true;
                if (hasBaseProductFilters) {
                    Integer finalProductId = (Integer) strainData.get("finalProductId");
                    Product finalProduct = productRepository.findById(finalProductId).orElse(null);

                    if (finalProduct == null) {
                        System.err.println("Final product ID " + finalProductId + " not found for container " + container.getContainerId());
                        passesBaseFilters = false; // Or skip?
                    } else {
                        // Filter by container's parent product min stock
                        if (passesBaseFilters && minStock != null) {
                            int parentProductId = (Integer) strainData.get("parentProductId");
                            int totalStock = inventoryStockRepository.sumStockByProductId(parentProductId);
                            if (totalStock < minStock) passesBaseFilters = false;
                        }

                        if (passesBaseFilters && !optionIds.isEmpty()) {
                            List<Integer> finalProductOptionIds = finalProduct.getProductOptions().stream()
                                    .map(ProductOption::getOptionId)
                                    .collect(Collectors.toList());
                            if (!finalProductOptionIds.containsAll(optionIds)) {
                                passesBaseFilters = false;
                            }
                        }
                    }
                }

                if (passesBaseFilters) {
                    finalFilteredContainers.add(container);
                    // --- Store the strain data we already built ---
                    Integer parentId = container.getParentProduct().getProductId();
                    finalContainersStrainMap.put(parentId, strainData); // Associate strain with parent ID
                    System.out.println("Container " + container.getContainerId() + " PASSED all filters.");
                } else {
                    System.out.println("Container " + container.getContainerId() + " FAILED base product filters.");
                }

                // --- 8. Update keyset pointers for next iteration (if more data is needed) ---
                if (finalFilteredContainers.size() < pageSize && hasMorePotentialData) {
                    if (!batchContainers.isEmpty()) {
                        if ("NEXT".equalsIgnoreCase(direction) || (direction == null || direction.isEmpty())) {
                            // For NEXT, the cursor moves forward based on the last fetched item in the batch.
                            afterId = batchContainers.get(batchContainers.size() - 1).getContainerId();
                        } else if ("PREVIOUS".equalsIgnoreCase(direction)) {
                            // For PREV, the query was DESC. The last item in the fetched list has the lowest ID.
                            // The next PREV query should fetch items with ID < this lowest ID.
                            beforeId = batchContainers.get(batchContainers.size() - 1).getContainerId();
                        }
                    } else {
                        // No items fetched in this batch, definitely no more data
                        hasMorePotentialData = false;
                    }
                }

            } // End of inner loop for base product filtering of batch

            totalFetched += batchContainers.size();
            System.out.println("End of fetch iteration. Total filtered so far: " + finalFilteredContainers.size() + ", Total fetched: " + totalFetched);

        } // End of fetch and filter loop

        // --- 9. Handle PREVIOUS page result ordering ---
        if ("PREVIOUS".equalsIgnoreCase(direction)) {
            java.util.Collections.reverse(finalFilteredContainers);
            // Reverse the strain map order if needed, but since it's a Map keyed by ID,
            // the association remains correct, just the display order of containers changes.
            System.out.println("Reversed list for PREVIOUS page display.");
        }

        // --- 10. Truncate to exact page size if we overfetched ---
        boolean truncated = false;
        if (finalFilteredContainers.size() > pageSize) {
            finalFilteredContainers = finalFilteredContainers.stream().limit(pageSize).collect(Collectors.toList());
            truncated = true; // Indicates we hit the page size limit
        }

        // --- 11. Determine Navigation Flags ---
        boolean hasNext;
        boolean hasPrevious;

        // --- hasPrevious Logic ---
        // Enable "Previous" button if the *original request* indicated it was NOT the first page.
        // This is signaled by the presence of firstContainerId (for PREV) or lastContainerId (for NEXT).
        hasPrevious = (firstContainerId != null) || (lastContainerId != null);

        // --- hasNext Logic ---
        // Enable "Next" button if we potentially filled the requested page.
        // This happens if we got exactly 'pageSize' items (indicating more might exist)
        // or if we got MORE than 'pageSize' and had to truncate.
        // If we got LESS than 'pageSize', we've likely reached the end of the data.
        hasNext = (finalFilteredContainers.size() >= pageSize);

        // Special case: If fetching PREVIOUS returned NO results,
        // it means the original page was the first page.
        // The flags returned should reflect the state of the *attempted fetch*.
        // The UI might need to handle an empty "prev" result specially.
        // For the returned *state* (empty list from PREV request):
        if ("PREVIOUS".equalsIgnoreCase(direction) && finalFilteredContainers.isEmpty()) {
            // The request was "get items before firstContainerId", but none were found.
            // This implies the page *starting at firstContainerId* was the first page.
            // The state we are returning (an empty list) has no next or previous relative to itself.
            // However, the UI context is that the user tried to go back from the first page.
            // The correct UI response is usually to disable Prev and potentially reload the original first page.
            // The flags for the *returned empty state* should ideally signal this.
            // Conventionally, an empty result set from a PREV request means no previous page relative to that empty state.
            // Whether there's a 'next' depends: if the original firstContainerId page existed, then yes.
            // But for simplicity, returning flags for the *empty result*:
            hasNext = false; // The empty list has no inherent 'next'
            hasPrevious = false; // The empty list has no inherent 'previous'
            // The UI logic needs to interpret this correctly (e.g., show "no results" or go back to original page).
        }


        // --- 12. Create the result map ---
        // We already have the correct list (finalFilteredContainers) and map (finalContainersStrainMap).
        // We need a Page object primarily for its structure to pass metadata. The number/size might not be strictly accurate for keyset.
        // Create a dummy pageable reflecting the request, or just use the page size.
        // The important flags are hasNext and hasPrevious.
        int currentPageNumber = 0; // Placeholder, keyset doesn't use traditional numbering easily
        Pageable resultPageable = PageRequest.of(currentPageNumber, pageSize, sort); // Sort might be ASC or DESC based on original request
        // Use the actual size of the final list for total elements for this "page"
        Page<Container> resultPage = new PageImpl<>(finalFilteredContainers, resultPageable, finalFilteredContainers.size());

        System.out.println("Filtering complete. Returning " + finalFilteredContainers.size() + " containers. hasNext: " + hasNext + ", hasPrevious: " + hasPrevious);

        return Map.of(
                "containersPage", resultPage,
                "containersMap", finalContainersStrainMap, // Use the map built during the loop
                "hasNext", hasNext,
                "hasPrevious", hasPrevious
                // Potentially add current direction or other context if the frontend needs it
        );
    }

    /**
     * Public method to delete a container by its parent product ID.
     * This is the entry point for external calls (e.g., from controller or ProductService).
     *
     * @param parentProductId The ID of the parent product identifying the container.
     * @return ResponseEntity indicating success (200 OK) or failure (404 Not Found, 500 Internal Server Error, 409 Conflict).
     */
    public ResponseEntity<?> deleteContainer(Integer parentProductId) {
        try {
            // 1. Delegate to the internal deletion logic
            return deleteContainerInternal(parentProductId);
        } catch (DataIntegrityViolationException ex) {
            System.err.println("DataIntegrityViolationException deleting container ID " + parentProductId + ": " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Cannot delete container due to existing references."));
        } catch (Exception ex) {
            System.err.println("Error deleting container with parent product ID " + parentProductId + ": " + ex.getMessage());
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred while deleting the container."));
        }
    }

    /**
     * Internal method containing the core deletion logic for a container.
     * Handles the chain reaction if the container's parent product is a child elsewhere.
     *
     * @param parentProductId The ID of the parent product identifying the container.
     * @return ResponseEntity indicating success (200 OK) or failure (404 Not Found).
     */
    private ResponseEntity<?> deleteContainerInternal(Integer parentProductId) {
        try {
            // --- STEP 1: (NEW) First, handle the chain reaction upwards. ---
            // Find any containers where the product we are about to delete is a CHILD.
            List<Container> containersReferencingAsChild = containerRepository.findByChildProduct_ProductId(parentProductId);

            if (!containersReferencingAsChild.isEmpty()) {
                System.out.println("Product ID " + parentProductId + " is a child in " +
                        containersReferencingAsChild.size() + " other container(s). Deleting them first.");
                // For each of those containers, recursively call this deletion logic.
                for (Container upstreamContainer : containersReferencingAsChild) {
                    Integer upstreamParentProductId = upstreamContainer.getParentProduct().getProductId();
                    // RECURSIVE CALL to handle the entire chain upwards.
                    this.deleteContainerInternal(upstreamParentProductId);
                }
            }

            // --- STEP 2: Now, delete the actual target container. ---
            // Find the container identified by the parentProductId.
            Optional<Container> containerOpt = containerRepository.findByParentProduct_ProductId(parentProductId);

            if (containerOpt.isPresent()) {
                Container containerToDelete = containerOpt.get();
                System.out.println("Deleting target container with parent product ID: " + parentProductId);

                // This will now succeed because the upstream dependencies are gone.
                // The JPA cascade will correctly remove the associated parent product (Product-12 in our example).
                containerRepository.delete(containerToDelete);

                return ResponseEntity.ok().build(); // 200 OK

            } else {
                // This case might occur if a recursive call already deleted it.
                System.out.println("Container with parent product ID " + parentProductId + " was not found (might have been deleted by a chain reaction).");
                // Returning OK is appropriate since the desired state (deletion) is achieved.
                return ResponseEntity.ok().build();
            }

        } catch (DataIntegrityViolationException ex) {
            // Re-throw to be caught by the public deleteContainer method.
            throw ex;
        } catch (Exception ex) {
            // Re-throw for the public method to handle.
            throw ex;
        }
    }


    /**
     * HELPER METHOD: Centralizes the logic for converting dimensions,
     * calculating volume, and updating a Product entity from a ContainerRequestDTO.
     * @param productToUpdate The product entity to be modified.
     * @param request The DTO containing the new dimension and price data.
     */
    private void updateParentProductFromRequest(Product productToUpdate, ContainerRequestDTO request) {
        // 1. Parse the unit from the request string
        MeasurementUnitEnum unit = MeasurementUnitEnum.valueOf(request.getDistanceUnit().toUpperCase());

        // 2. Get the conversion factor to the base unit (cm)
        BigDecimal factor = unit.getToBaseFactor();

        // 3. Convert all dimensions to the base unit (cm)
        BigDecimal heightInCm = request.getHeight().multiply(factor);
        BigDecimal widthInCm = request.getWidth().multiply(factor);
        BigDecimal lengthInCm = request.getLength().multiply(factor);

        // 4. Calculate the volume in the base unit (cm³)
        BigDecimal volumeInCm3 = heightInCm.multiply(widthInCm).multiply(lengthInCm);

        // 5. Set the updated and calculated values on the product entity
        productToUpdate.setPrice(request.getParentProductPrice());
        productToUpdate.setHeight(heightInCm);
        productToUpdate.setWidth(widthInCm);
        productToUpdate.setLength(lengthInCm);
        productToUpdate.setVolume(volumeInCm3); // Update the calculated volume
    }

    /**
     * Builds a concise, human-readable information string for any product.
     * - If the product is a base product, it returns its options (e.g., "Break, Chocolate, 250g").
     * - If the product is a container, it recursively builds the hierarchy string
     * (e.g., "BOX contains 20 -> PACK contains 10 -> Break, Chocolate, 250g").
     *
     * @param product The Product entity to describe.
     * @return A descriptive string for the product.
     */
    public String buildProductInfoString(Product product) {
        if (product == null) {
            return "N/A";
        }

        // Check if the product is a parent of a container
        Container container = product.getContainer();

        if (container == null) {
            // This is a base product, so just return its options.
            return product.getProductOptionsDisplay();
        } else {
            // This is a container, so build the hierarchy string.
            StringBuilder infoBuilder = new StringBuilder();
            Container currentContainer = container;

            while (currentContainer != null) {
                infoBuilder.append(currentContainer.getUnit().toString())
                        .append(" contains ")
                        .append(currentContainer.getQuantity());

                Product childProduct = currentContainer.getChildProduct();
                Container nextContainer = childProduct.getContainer(); // Check if the child is also a container

                if (nextContainer != null) {
                    infoBuilder.append(" -> ");
                    currentContainer = nextContainer;
                } else {
                    // Reached the final base product at the end of the chain
                    String finalOptions = childProduct.getProductOptionsDisplay();
                    if (!finalOptions.isEmpty()) {
                        infoBuilder.append(" -> ").append(finalOptions);
                    }
                    currentContainer = null; // End the loop
                }
            }
            return infoBuilder.toString();
        }
    }
}
