package com.speedit.inventorysystem.service;

import com.speedit.inventorysystem.controller.ProductController.ProductRequest;
import com.speedit.inventorysystem.dto.ProductDTO;
import com.speedit.inventorysystem.model.OptionCategory;
import com.speedit.inventorysystem.model.Product;
import com.speedit.inventorysystem.model.ProductOption;
import com.speedit.inventorysystem.repository.InventoryStockRepository;
import com.speedit.inventorysystem.repository.OptionCategoryRepository;
import com.speedit.inventorysystem.repository.ProductOptionRepository;
import com.speedit.inventorysystem.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Autowired private OptionCategoryRepository optionCategoryRepository;
    @Autowired private ProductOptionRepository productOptionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryStockRepository inventoryStockRepository;

    @Autowired private BarcodeService barcodeService;

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
                newCatIndex++;
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
                newOptIndex++;
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

    public void createProduct(Long price, List<ProductOption> finalOptions) {
        Product product = new Product();
        product.setPrice(price);
        product.setProductOptions(finalOptions);
        productRepository.save(product); // Save first to get ID

        int checksum = calculateChecksumDigit(product.getProductId());
        product.setBarcodeChecksum(checksum);

        productRepository.save(product); // Update with checksum
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

    public List<Product> getAllProducts() {
        return productRepository.findAll();
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
                    product.getCreatedAt(),
                    product.getCreatedBy(),
                    product.getUpdatedAt(),
                    product.getUpdatedBy(),
                    barcodeImage
            );
        });
    }

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

            createProduct(request.getPrice(), options);
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

            Product product = existingProduct.get();
            product.setPrice(request.getPrice());
            product.setProductOptions(options);
            productRepository.save(product);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating product: " + e.getMessage());
        }
    }

    public ResponseEntity<?> deleteProduct(Integer id) {
        try {
            if (productRepository.existsById(id)) {
                productRepository.deleteById(id);
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting product: " + e.getMessage());
        }
    }

    public List<Product> filterProducts(Long minPrice, Long maxPrice, Integer minStock, List<Integer> optionIds) {
        return productRepository.findByFilters(minPrice, maxPrice, minStock, optionIds);
    }

    // Full barcode
    // String barcode = countryPrefix + companyPrefix + String.format("%05d", product.getProductId()) + product.getBarcodeChecksum();
}
