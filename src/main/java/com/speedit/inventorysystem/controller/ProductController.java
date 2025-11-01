package com.speedit.inventorysystem.controller;

import com.speedit.inventorysystem.dto.ProductDTO;
import com.speedit.inventorysystem.model.*;
import com.speedit.inventorysystem.repository.ProductRepository;
import com.speedit.inventorysystem.service.ProductService;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Value("${barcode.prefix.country}")
    private String countryPrefix;

    @Value("${barcode.prefix.company}")
    private String companyPrefix;

    @GetMapping("/manage")
    public String manageProducts(Model model,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        try {
            // Define default sorting (e.g., by productId)
            Sort sort = Sort.by(Sort.Direction.ASC, "productId"); // Adjust field name if different
            Pageable pageable = PageRequest.of(page, size, sort);

            // Load PAGINATED base products
            Page<Product> productPage = productService.getAllProducts(pageable);

            Map<String, Object> formData = productService.prepareAddProductData();

            // Add the page object (contains data and metadata) to the model
            model.addAttribute("productPage", productPage);
            // Keep formData as before
            model.addAttribute("categories", formData.get("categories"));
            model.addAttribute("categoryOptionsMap", formData.get("categoryOptionsMap"));

        } catch (Exception e) {
            System.err.println("Error fetching product data for management page: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("errorMessage", "Failed to load product data.");
            // Return an error view or the page with limited data/indication
            // For now, we'll still return the page, but it might be empty or show error
        }

        return "manage-product";
    }

    @GetMapping("/{id}/details")
    @ResponseBody
    public ResponseEntity<ProductDTO> getProductDetails(@PathVariable Integer id) {
        return productService.getProductDetails(id, countryPrefix, companyPrefix)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createProduct(
            @Valid @RequestBody ProductRequest request) {
        return productService.createProductFromRequest(request);
    }

    @PutMapping("/{id}/update")
    @ResponseBody
    public ResponseEntity<?> updateProduct(
            @PathVariable Integer id,
            @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteProduct(@PathVariable Integer id) {
        return productService.deleteProduct(id);
    }

    @PostMapping("/filter")
    @ResponseBody
    public ResponseEntity<List<Product>> filterProducts(@RequestBody ProductFilterRequest request) {
        try {
            // Pass all filter criteria from the request to the service method
            List<Product> products = productService.filterProducts(
                    request.getMinPrice(),
                    request.getMaxPrice(),
                    request.getMinStock(),
                    request.getOptionIds()
            );
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            // Log the exception
            // Return an appropriate error response
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // Or a more descriptive error
        }
    }

    @Data
    public static class ProductFilterRequest {
        private Long minPrice;
        private Long maxPrice;
        private Integer minStock;
        private List<Integer> optionIds;
    }

    // Request DTO for product operations
    @Data
    public static class ProductRequest {
        private List<String> categoryIds;
        private List<String> optionIds;
        private List<String> newCategoryNames;
        private List<String> newOptionValues;
        private BigDecimal price;
        private String distanceUnit;
        private BigDecimal height;
        private BigDecimal width;
        private BigDecimal length;
    }
}