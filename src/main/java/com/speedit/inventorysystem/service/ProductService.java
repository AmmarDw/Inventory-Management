package com.speedit.inventorysystem.service;

import com.speedit.inventorysystem.controller.ProductController.ProductRequest;
import com.speedit.inventorysystem.dto.ProductDTO;
import com.speedit.inventorysystem.enums.MeasurementUnitEnum;
import com.speedit.inventorysystem.model.Container;
import com.speedit.inventorysystem.model.OptionCategory;
import com.speedit.inventorysystem.model.Product;
import com.speedit.inventorysystem.model.ProductOption;
import com.speedit.inventorysystem.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Autowired private OptionCategoryRepository optionCategoryRepository;
    @Autowired private ProductOptionRepository productOptionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryStockRepository inventoryStockRepository;
    @Autowired private ContainerRepository containerRepository;

    @Autowired private BarcodeService barcodeService;
    @Autowired @Lazy
    private ContainerService containerService;

    @Value("${barcode.prefix.country}")
    private String countryPrefix;
    @Value("${barcode.prefix.company}")
    private String companyPrefix;


    /**
     * Load categories and build DTO map: categoryId → list of {optionId, optionValue}
     */
    public Map<String,Object> prepareAddProductData() {
        List<OptionCategory> categories = optionCategoryRepository.findAll();
        log.debug("▶ Preparing AddProduct data: {} categories", categories.size());

        Map<Integer,List<Map<String,Object>>> categoryOptionsMap = new HashMap<>();
        for (OptionCategory cat : categories) {
            List<Map<String,Object>> dtoList = cat.getOptions().stream()
                    .map(opt -> {
                        Map<String,Object> m = new HashMap<>();
                        m.put("optionId", opt.getOptionId());
                        m.put("optionValue", opt.getOptionValue());
                        return m;
                    })
                    .collect(Collectors.toList());
            categoryOptionsMap.put(cat.getOptionCategoryId(), dtoList);
        }

        Map<String,Object> result = new HashMap<>();
        result.put("categories", categories);
        result.put("categoryOptionsMap", categoryOptionsMap);
        return result;
    }

    /**
     * Process selections, create new entities, and return final options list.
     */
    public List<ProductOption> buildProductOptions(
            List<String> categoryIds,
            List<String> optionIds,
            List<String> newCategoryNames,
            List<String> newOptionValues,
            Model model // to return errors to the view
    ) {
        List<ProductOption> finalOptions = new ArrayList<>();
        int newCatIndex = 0, newOptIndex = 0;

        for (int i = 0; i < categoryIds.size(); i++) {
            String catId = categoryIds.get(i);
            String optId = optionIds.get(i);
            OptionCategory category;

            // Handle new category
            if ("new".equalsIgnoreCase(catId)) {
                String categoryName = newCategoryNames.get(newCatIndex++);
                Optional<OptionCategory> existing = optionCategoryRepository.findByCategoryName(categoryName);
                if (existing.isPresent()) {
                    model.addAttribute("error", "A category named \"" + categoryName + "\" already exists.");
                    return null;
                }
                category = new OptionCategory();
                category.setCategoryName(categoryName);
                optionCategoryRepository.save(category);
            } else {
                category = optionCategoryRepository
                        .findById(Integer.parseInt(catId)).orElse(null);
            }

            ProductOption option;

            // Handle new option
            if ("new".equalsIgnoreCase(optId)) {
                String optionValue = newOptionValues.get(newOptIndex++);
                Optional<ProductOption> existingOpt =
                        productOptionRepository.findByOptionValueAndCategory(optionValue, category);
                if (existingOpt.isPresent()) {
                    model.addAttribute("error", "Option \"" + optionValue + "\" already exists in category \"" + category.getCategoryName() + "\".");
                    return null;
                }

                option = new ProductOption();
                option.setOptionValue(optionValue);
                option.setCategory(category);
                productOptionRepository.save(option);
            } else {
                option = productOptionRepository
                        .findById(Integer.parseInt(optId)).orElse(null);
            }

            if (option != null) finalOptions.add(option);
        }

        return finalOptions;
    }

    public boolean isDuplicateProduct(List<ProductOption> options) {
        return productRepository.findAll().stream()
                .anyMatch(p -> new HashSet<>(p.getProductOptions())
                        .equals(new HashSet<>(options)));
    }

    public Product createProduct(Product product) {
        productRepository.save(product); // Save first to get ID

        int checksum = calculateChecksumDigit(product.getProductId());
        product.setBarcodeChecksum(checksum);

        return productRepository.save(product); // Update with checksum
    }

    public int calculateChecksumDigit(Integer productId) {
        String productRef = String.format("%05d", productId); // 5-digit product reference
        String rawCode = countryPrefix + companyPrefix + productRef; // 12-digit barcode base

        if (rawCode.length() != 12 || !rawCode.matches("\\d{12}")) {
            throw new IllegalArgumentException("Invalid barcode base: " + rawCode);
        }

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(rawCode.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;
        }

        return (10 - (sum % 10)) % 10; // Final checksum digit
    }

    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findBaseProductsWithPagination(pageable);
    }

    public Optional<ProductDTO> getProductDetails(Integer id, String countryPrefix, String companyPrefix) {
        return productRepository.findById(id).map(product -> {
            // Calculate total stock
            int totalStock = inventoryStockRepository.sumStockByProductId(id);

            // Prepare product options for DTO
            List<ProductDTO.ProductOptionDTO> optionDTOs = product.getProductOptions().stream()
                    .map(option -> new ProductDTO.ProductOptionDTO(
                            option.getOptionId(),
                            option.getOptionValue(),
                            option.getCategory().getOptionCategoryId(),
                            option.getCategory().getCategoryName()
                    ))
                    .collect(Collectors.toList());

            // Build full barcode
            String productRef = String.format("%05d", product.getProductId());
            String fullBarcode = countryPrefix + companyPrefix + productRef + product.getBarcodeChecksum();

            // Generate barcode image
            byte[] barcodeImage = new byte[0];
            try {
                barcodeImage = barcodeService.generateBarcodeImage(fullBarcode, 300, 80);
            } catch (Exception e) {
                log.error("Error generating barcode for product {}", id, e);
            }

                return new ProductDTO(
                    product.getProductId(),
                    fullBarcode,
                    product.getPrice(),
                    optionDTOs,
                    totalStock,
                    product.getVolume(),
                    product.getHeight(),
                    product.getWidth(),
                    product.getLength(),
                    product.getCreatedAt(),
                    product.getCreatedBy(),
                    product.getUpdatedAt(),
                    product.getUpdatedBy(),
                    barcodeImage
            );
        });
    }

    @Transactional
    public ResponseEntity<?> createProductFromRequest(ProductRequest request) {
        try {
            List<ProductOption> options = buildProductOptions(
                    request.getCategoryIds(),
                    request.getOptionIds(),
                    request.getNewCategoryNames(),
                    request.getNewOptionValues(),
                    null
            );

            if (options == null) {
                return ResponseEntity.badRequest().body("Error creating options");
            }

            // Check for duplicate product
            if (isDuplicateProduct(options)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Product with the same options already exists");
            }

            MeasurementUnitEnum unit = MeasurementUnitEnum.valueOf(request.getDistanceUnit().toUpperCase());
            BigDecimal factor = unit.getToBaseFactor();

            BigDecimal heightInCm = request.getHeight().multiply(factor);
            BigDecimal widthInCm = request.getWidth().multiply(factor);
            BigDecimal lengthInCm = request.getLength().multiply(factor);
            BigDecimal volumeInCm3 = heightInCm.multiply(widthInCm).multiply(lengthInCm);

            Product product = new Product();
            product.setPrice(request.getPrice());
            product.setProductOptions(options);
            product.setHeight(heightInCm);
            product.setWidth(widthInCm);
            product.setLength(lengthInCm);
            product.setVolume(volumeInCm3);

            createProduct(product);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating product: " + e.getMessage());
        }
    }

    public ResponseEntity<?> updateProduct(Integer id, ProductRequest request) {
        try {
            Optional<Product> existingProduct = productRepository.findById(id);
            if (!existingProduct.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            List<ProductOption> options = buildProductOptions(
                    request.getCategoryIds(),
                    request.getOptionIds(),
                    request.getNewCategoryNames(),
                    request.getNewOptionValues(),
                    null
            );

            if (options == null) {
                return ResponseEntity.badRequest().body("Error creating options");
            }

            MeasurementUnitEnum unit = MeasurementUnitEnum.valueOf(request.getDistanceUnit().toUpperCase());
            BigDecimal factor = unit.getToBaseFactor();

            BigDecimal heightInCm = request.getHeight().multiply(factor);
            BigDecimal widthInCm = request.getWidth().multiply(factor);
            BigDecimal lengthInCm = request.getLength().multiply(factor);
            BigDecimal volumeInCm3 = heightInCm.multiply(widthInCm).multiply(lengthInCm);

            Product product = existingProduct.get();
            product.setPrice(request.getPrice());
            product.setProductOptions(options);
            product.setHeight(heightInCm);
            product.setWidth(widthInCm);
            product.setLength(lengthInCm);
            product.setVolume(volumeInCm3);
            productRepository.save(product);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating product: " + e.getMessage());
        }
    }

    /**
     * Deletes a product.
     * If the product is a base product (child of containers), it finds and deletes
     * the containers referencing it, triggering a chain reaction.
     * If the product is a parent container product, it relies on JPA/database cascade.
     *
     * @param id The ID of the product to delete.
     * @return ResponseEntity indicating success (200 OK) or failure (404 Not Found, 500 Internal Server Error, 409 Conflict).
     */
    public ResponseEntity<?> deleteProduct(Integer id) {
        try {
            // 1. Check if the product exists
            if (!productRepository.existsById(id)) {
                System.out.println("Product with ID " + id + " not found for deletion.");
                return ResponseEntity.notFound().build(); // 404 Not Found
            }

            // 2. --- Chain Reaction Logic: Find containers where this product is the CHILD ---
            // Find all containers where child_product_id = :id
            List<Container> containersReferencingAsChild = containerRepository.findByChildProduct_ProductId(id);

            if (!containersReferencingAsChild.isEmpty()) {
                System.out.println("Product ID " + id + " is a child in " + containersReferencingAsChild.size() + " container(s). Initiating chain reaction deletion.");
                // 3. For each container referencing this product as child, delete the container (and its parent product)
                // This will trigger the JPA cascade (Container -> parentProduct) and DB cascade (parent_product_id ON DELETE CASCADE)
                for (Container container : containersReferencingAsChild) {
                    Integer parentProductId = container.getParentProduct().getProductId();
                    System.out.println("Deleting container with parent product ID: " + parentProductId);
                    // --- CRITICAL: Call ContainerService to handle potential further chain reactions ---
                    // This ensures if the parent product is itself a child elsewhere, that chain is also handled.
                    containerService.deleteContainer(parentProductId); // Use parent product ID
                    // Note: containerService.deleteContainer should handle the actual deletion of the container entity
                    // and its parent product, leveraging JPA/DB cascades for that direct relationship.
                }
            } else {
                System.out.println("Product ID " + id + " is not a child product in any container.");
            }

            // 4. --- Direct Deletion ---
            // Now, delete the product itself.
            // If it was a parent container product, the JPA cascade (Product.container) and DB cascade (parent_product_id)
            // should handle deleting the Container and then the parent Product.
            // If it was a base product, it should be deleted now.
            System.out.println("Deleting product with ID: " + id);
            productRepository.deleteById(id);
            System.out.println("Product with ID " + id + " deleted successfully.");

            return ResponseEntity.ok().build(); // 200 OK

        } catch (DataIntegrityViolationException ex) {
            // 5. Handle database constraint violations (e.g., referenced by InventoryStock, OrderItem)
            System.err.println("DataIntegrityViolationException deleting product ID " + id + ": " + ex.getMessage());
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Cannot delete product due to existing references (e.g., in inventory stock or orders)."));
        } catch (Exception ex) {
            // 6. Handle any other unexpected errors
            System.err.println("Error deleting product with ID " + id + ": " + ex.getMessage());
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred while deleting the product."));
        }
    }

    public List<Product> filterProducts(Long minPrice, Long maxPrice, Integer minStock, List<Integer> optionIds) {
        return productRepository.findByFilters(minPrice, maxPrice, minStock, optionIds);
    }

    // Full barcode
    // String barcode = countryPrefix + companyPrefix + String.format("%05d", product.getProductId()) + product.getBarcodeChecksum();
}
