package com.speedit.inventorysystem.controller;

import com.speedit.inventorysystem.model.ProductOption;
import com.speedit.inventorysystem.model.Product;
import com.speedit.inventorysystem.service.ProductService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductService productService;

    @GetMapping("/add")
    public String showAddProductForm(Model model) {
        // 1. load all categories
        Map<String, Object> data = productService.prepareAddProductData();
        log.debug("▶ GET /products/add: loaded {} categories", ((List<?>) data.get("categories")).size());
        log.debug("▶ GET /products/add: categories: {}", data.get("categories"));
        log.debug("▶ GET /products/add: categoryOptionsMap = {}", data.get("categoryOptionsMap"));

        model.addAttribute("categories", data.get("categories"));
        model.addAttribute("categoryOptionsMap", data.get("categoryOptionsMap"));

        return "add-product";
    }

    @PostMapping("/add")
    public String addProduct(
            @RequestParam(value = "categoryIds", required = false) List<String> categoryIds,
            @RequestParam(value = "optionIds", required = false) List<String> optionIds,
            @RequestParam(value = "newCategoryNames", required = false) List<String> newCategoryNames,
            @RequestParam(value = "newOptionValues", required = false) List<String> newOptionValues,
            @RequestParam("price") Long price,
            Model model) {

        log.debug("▶ POST /products/add:");
        log.debug("   categoryIds      = {}", categoryIds);
        log.debug("   optionIds        = {}", optionIds);
        log.debug("   newCategoryNames = {}", newCategoryNames);
        log.debug("   newOptionValues  = {}", newOptionValues);
        log.debug("   price            = {}", price);

        if ((categoryIds == null || categoryIds.isEmpty()) &&
                (newCategoryNames == null || newCategoryNames.isEmpty())) {

            model.addAttribute("error", "You must select or add at least one product option.");
            return showAddProductForm(model);
        }

        List<ProductOption> finalOptions = productService.buildProductOptions(
                categoryIds, optionIds, newCategoryNames, newOptionValues, model);

        // For duplicate category or option
        if (finalOptions == null) {
            return showAddProductForm(model);
        }

        boolean duplicate = productService.isDuplicateProduct(finalOptions);
        if (duplicate) {
            model.addAttribute("error", "Product with the same options already exists.");
            return showAddProductForm(model);
        }

        productService.createProduct(price, finalOptions);
        return "redirect:/products/list";
    }
}
