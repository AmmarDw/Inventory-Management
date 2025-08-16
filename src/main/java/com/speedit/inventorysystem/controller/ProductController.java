package com.speedit.inventorysystem.controller;

import com.speedit.inventorysystem.dto.ProductDTO;
import com.speedit.inventorysystem.model.*;
import com.speedit.inventorysystem.service.ProductService;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
    public String manageProducts(Model model) {
        // Load products with stock information
        List<Product> products = productService.getAllProducts();
        Map<String, Object> formData = productService.prepareAddProductData();

        model.addAttribute("products", products);
        model.addAttribute("categories", formData.get("categories"));
        model.addAttribute("categoryOptionsMap", formData.get("categoryOptionsMap"));
        System.out.println("categoryOptionsMap: " + formData.get("categoryOptionsMap"));
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
        private Long price;
    }
}