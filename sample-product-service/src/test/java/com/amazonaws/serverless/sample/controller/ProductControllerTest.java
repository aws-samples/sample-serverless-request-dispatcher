// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.controller;

import com.amazonaws.serverless.sample.model.Product;
import com.amazonaws.serverless.sample.service.ProductService;
import com.amazonaws.serverless.requestdispatcher.annotation.RouteException;
import com.google.gson.Gson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ProductControllerTest {

    private ProductService productService;
    private Gson gson;
    private ProductController controller;

    @BeforeEach
    public void setUp() {
        productService = mock(ProductService.class);
        gson = new Gson();
        controller = new ProductController(productService, gson);
    }

    // --- listProducts ---

    @Test
    public void listProductsWithNullCategoryReturnsAll() {
        Product p1 = new Product("1", "Widget", "Hardware", 9.99, null, null);
        Product p2 = new Product("2", "Gadget", "Electronics", 19.99, null, null);
        when(productService.findAll()).thenReturn(List.of(p1, p2));

        String result = controller.listProducts(null);

        verify(productService).findAll();
        verify(productService, never()).findByCategory(any());
        assertTrue(result.contains("Widget"));
        assertTrue(result.contains("Gadget"));
    }

    @Test
    public void listProductsWithCategoryFilters() {
        Product p1 = new Product("1", "Keyboard", "Electronics", 79.99, null, null);
        when(productService.findByCategory("Electronics")).thenReturn(List.of(p1));

        String result = controller.listProducts("Electronics");

        verify(productService).findByCategory("Electronics");
        verify(productService, never()).findAll();
        assertTrue(result.contains("Keyboard"));
    }

    @Test
    public void listProductsEmptyListReturnsEmptyArray() {
        when(productService.findAll()).thenReturn(List.of());

        String result = controller.listProducts(null);

        assertEquals("[]", result);
    }

    // --- getProduct ---

    @Test
    public void getProductReturnsProductJson() {
        Product product = new Product("abc", "Laptop", "Electronics", 999.99, "A laptop", null);
        when(productService.findById("abc")).thenReturn(product);

        String result = controller.getProduct("abc");

        assertTrue(result.contains("\"id\":\"abc\""));
        assertTrue(result.contains("\"name\":\"Laptop\""));
    }

    @Test
    public void getProductNotFoundThrowsRouteException() {
        when(productService.findById("nonexistent")).thenReturn(null);

        RouteException ex = assertThrows(RouteException.class,
                () -> controller.getProduct("nonexistent"));
        assertTrue(ex.getMessage().contains("Product not found"));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    // --- createProduct ---

    @Test
    public void createProductValidInput() {
        Product created = new Product("gen-id", "Mouse", "Accessories", 29.99, null, null);
        when(productService.create(any(Product.class))).thenReturn(created);

        String body = "{\"name\":\"Mouse\",\"category\":\"Accessories\",\"price\":29.99}";
        String result = controller.createProduct(body);

        verify(productService).create(any(Product.class));
        assertTrue(result.contains("gen-id"));
        assertTrue(result.contains("Mouse"));
    }

    @Test
    public void createProductNullBodyThrowsRouteException() {
        RouteException ex = assertThrows(RouteException.class,
                () -> controller.createProduct("null"));
        assertTrue(ex.getMessage().contains("Request body is required"));
    }

    @Test
    public void createProductEmptyNameThrowsRouteException() {
        String body = "{\"name\":\"\",\"category\":\"Test\"}";
        RouteException ex = assertThrows(RouteException.class,
                () -> controller.createProduct(body));
        assertTrue(ex.getMessage().contains("Product name is required"));
    }

    @Test
    public void createProductBlankNameThrowsRouteException() {
        String body = "{\"name\":\"   \",\"category\":\"Test\"}";
        RouteException ex = assertThrows(RouteException.class,
                () -> controller.createProduct(body));
        assertTrue(ex.getMessage().contains("Product name is required"));
    }

    @Test
    public void createProductNameTooLongThrowsRouteException() {
        String longName = "x".repeat(257);
        String body = "{\"name\":\"" + longName + "\"}";
        RouteException ex = assertThrows(RouteException.class,
                () -> controller.createProduct(body));
        assertTrue(ex.getMessage().contains("maximum length of 256"));
    }

    @Test
    public void createProductCategoryTooLongThrowsRouteException() {
        String longCategory = "c".repeat(129);
        String body = "{\"name\":\"Valid\",\"category\":\"" + longCategory + "\"}";
        RouteException ex = assertThrows(RouteException.class,
                () -> controller.createProduct(body));
        assertTrue(ex.getMessage().contains("category exceeds maximum length"));
    }

    @Test
    public void createProductDescriptionTooLongThrowsRouteException() {
        String longDesc = "d".repeat(2049);
        String body = "{\"name\":\"Valid\",\"description\":\"" + longDesc + "\"}";
        RouteException ex = assertThrows(RouteException.class,
                () -> controller.createProduct(body));
        assertTrue(ex.getMessage().contains("description exceeds maximum length"));
    }

    @Test
    public void createProductNegativePriceThrowsRouteException() {
        String body = "{\"name\":\"Valid\",\"price\":-1.0}";
        RouteException ex = assertThrows(RouteException.class,
                () -> controller.createProduct(body));
        assertTrue(ex.getMessage().contains("price must not be negative"));
    }

    @Test
    public void createProductZeroPriceIsValid() {
        Product created = new Product("id", "Free Item", null, 0.0, null, null);
        when(productService.create(any(Product.class))).thenReturn(created);

        String body = "{\"name\":\"Free Item\",\"price\":0.0}";
        String result = controller.createProduct(body);

        verify(productService).create(any(Product.class));
        assertTrue(result.contains("Free Item"));
    }

    @Test
    public void createProductNullPriceIsValid() {
        Product created = new Product("id", "No Price", null, null, null, null);
        when(productService.create(any(Product.class))).thenReturn(created);

        String body = "{\"name\":\"No Price\"}";
        String result = controller.createProduct(body);

        verify(productService).create(any(Product.class));
    }

    // --- updateProduct ---

    @Test
    public void updateProductSetsIdFromPath() {
        Product updated = new Product("path-id", "Updated", "Cat", 50.0, null, null);
        when(productService.update(any(Product.class))).thenReturn(updated);

        String body = "{\"name\":\"Updated\",\"category\":\"Cat\",\"price\":50.0}";
        String result = controller.updateProduct("path-id", body);

        verify(productService).update(argThat(p -> "path-id".equals(p.getId())));
        assertTrue(result.contains("path-id"));
    }

    @Test
    public void updateProductValidatesInput() {
        String body = "{\"name\":\"\"}";
        assertThrows(RouteException.class,
                () -> controller.updateProduct("id", body));
    }

    // --- deleteProduct ---

    @Test
    public void deleteProductCallsServiceAndReturnsMessage() {
        String result = controller.deleteProduct("del-id");

        verify(productService).delete("del-id");
        assertTrue(result.contains("Product deleted"));
    }
}
