// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.controller;

import com.amazonaws.serverless.sample.model.Product;
import com.amazonaws.serverless.sample.service.ProductService;
import com.amazonaws.serverless.requestdispatcher.annotation.*;
import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.inject.Inject;
import java.util.List;

public class ProductController {

    private final ProductService productService;
    private final Gson gson;

    @Inject
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Dagger-managed singletons injected via constructor DI")
    public ProductController(ProductService productService, Gson gson) {
        this.productService = productService;
        this.gson = gson;
    }

    @GetMapping("/products")
    public String listProducts(@RequestParam("category") String category) {
        List<Product> products = category != null
            ? productService.findByCategory(category)
            : productService.findAll();
        return gson.toJson(products);
    }

    @GetMapping("/products/{id}")
    public String getProduct(@PathVariable("id") String id) {
        Product product = productService.findById(id);
        if (product == null) {
            throw new RouteException("Product not found: " + id);
        }
        return gson.toJson(product);
    }

    @PostMapping("/products")
    public String createProduct(String body) {
        Product product = gson.fromJson(body, Product.class);
        validateProduct(product);
        Product created = productService.create(product);
        return gson.toJson(created);
    }

    @PutMapping("/products/{id}")
    public String updateProduct(@PathVariable("id") String id, String body) {
        Product product = gson.fromJson(body, Product.class);
        product.setId(id);
        validateProduct(product);
        Product updated = productService.update(product);
        return gson.toJson(updated);
    }

    private void validateProduct(Product product) {
        if (product == null) {
            throw new RouteException("Request body is required");
        }
        if (product.getName() == null || product.getName().isBlank()) {
            throw new RouteException("Product name is required");
        }
        if (product.getName().length() > 256) {
            throw new RouteException("Product name exceeds maximum length of 256");
        }
        if (product.getCategory() != null && product.getCategory().length() > 128) {
            throw new RouteException("Product category exceeds maximum length of 128");
        }
        if (product.getDescription() != null && product.getDescription().length() > 2048) {
            throw new RouteException("Product description exceeds maximum length of 2048");
        }
        if (product.getPrice() != null && product.getPrice() < 0) {
            throw new RouteException("Product price must not be negative");
        }
    }

    @DeleteMapping("/products/{id}")
    public String deleteProduct(@PathVariable("id") String id) {
        productService.delete(id);
        return "{\"message\": \"Product deleted\"}";
    }
}
