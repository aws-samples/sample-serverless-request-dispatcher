// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.service;

import com.amazonaws.serverless.sample.model.Product;
import com.amazonaws.serverless.sample.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ProductServiceTest {

    private ProductRepository productRepository;
    private S3StorageService s3StorageService;
    private ProductService productService;

    @BeforeEach
    public void setUp() {
        productRepository = mock(ProductRepository.class);
        s3StorageService = mock(S3StorageService.class);
        productService = new ProductService(productRepository, s3StorageService);
    }

    // --- findAll ---

    @Test
    public void findAllDelegatesToRepository() {
        Product p1 = new Product("1", "A", "Cat1", 10.0, null, null);
        when(productRepository.findAll()).thenReturn(List.of(p1));

        List<Product> result = productService.findAll();

        assertEquals(1, result.size());
        assertEquals("A", result.get(0).getName());
        verify(productRepository).findAll();
    }

    // --- findByCategory ---

    @Test
    public void findByCategoryFiltersCaseInsensitive() {
        Product electronics = new Product("1", "Phone", "Electronics", 500.0, null, null);
        Product hardware = new Product("2", "Hammer", "Hardware", 15.0, null, null);
        when(productRepository.findAll()).thenReturn(List.of(electronics, hardware));

        List<Product> result = productService.findByCategory("electronics");

        assertEquals(1, result.size());
        assertEquals("Phone", result.get(0).getName());
    }

    @Test
    public void findByCategorySkipsProductsWithNullCategory() {
        Product noCategory = new Product("1", "Mystery", null, 5.0, null, null);
        Product withCategory = new Product("2", "Phone", "Electronics", 500.0, null, null);
        when(productRepository.findAll()).thenReturn(List.of(noCategory, withCategory));

        List<Product> result = productService.findByCategory("Electronics");

        assertEquals(1, result.size());
        assertEquals("Phone", result.get(0).getName());
    }

    @Test
    public void findByCategoryReturnsEmptyWhenNoMatch() {
        Product p = new Product("1", "Item", "Food", 3.0, null, null);
        when(productRepository.findAll()).thenReturn(List.of(p));

        List<Product> result = productService.findByCategory("Electronics");

        assertTrue(result.isEmpty());
    }

    // --- findById ---

    @Test
    public void findByIdDelegatesToRepository() {
        Product p = new Product("id1", "Widget", "Cat", 10.0, null, null);
        when(productRepository.findById("id1")).thenReturn(p);

        Product result = productService.findById("id1");

        assertEquals("Widget", result.getName());
    }

    @Test
    public void findByIdReturnsNullWhenNotFound() {
        when(productRepository.findById("missing")).thenReturn(null);

        assertNull(productService.findById("missing"));
    }

    // --- create ---

    @Test
    public void createDelegatesToRepository() {
        Product input = new Product(null, "New", "Cat", 20.0, null, null);
        Product saved = new Product("gen-id", "New", "Cat", 20.0, null, null);
        when(productRepository.create(input)).thenReturn(saved);

        Product result = productService.create(input);

        assertEquals("gen-id", result.getId());
        verify(productRepository).create(input);
    }

    // --- update ---

    @Test
    public void updateDelegatesToRepository() {
        Product input = new Product("id1", "Updated", "Cat", 30.0, null, null);
        when(productRepository.update(input)).thenReturn(input);

        Product result = productService.update(input);

        assertEquals("Updated", result.getName());
        verify(productRepository).update(input);
    }

    // --- delete ---

    @Test
    public void deleteWithImageKeyDeletesFromS3() {
        Product product = new Product("id1", "Item", "Cat", 10.0, null, "products/id1/image");
        when(productRepository.findById("id1")).thenReturn(product);

        productService.delete("id1");

        verify(s3StorageService).deleteFile("products/id1/image");
        verify(productRepository).delete("id1");
    }

    @Test
    public void deleteWithoutImageKeySkipsS3() {
        Product product = new Product("id1", "Item", "Cat", 10.0, null, null);
        when(productRepository.findById("id1")).thenReturn(product);

        productService.delete("id1");

        verify(s3StorageService, never()).deleteFile(any());
        verify(productRepository).delete("id1");
    }

    @Test
    public void deleteNonExistentProductSkipsS3() {
        when(productRepository.findById("missing")).thenReturn(null);

        productService.delete("missing");

        verify(s3StorageService, never()).deleteFile(any());
        verify(productRepository).delete("missing");
    }

    // --- uploadProductImage ---

    @Test
    public void uploadProductImageStoresAndUpdatesProduct() {
        Product product = new Product("p1", "Item", "Cat", 10.0, null, null);
        when(productRepository.findById("p1")).thenReturn(product);
        when(productRepository.update(any())).thenReturn(product);

        byte[] imageData = new byte[]{1, 2, 3};
        String key = productService.uploadProductImage("p1", imageData, "image/png");

        assertEquals("products/p1/image", key);
        verify(s3StorageService).uploadFile("products/p1/image", imageData, "image/png");
        verify(productRepository).update(argThat(p -> "products/p1/image".equals(p.getImageKey())));
    }

    @Test
    public void uploadProductImageForNonExistentProductStillUploads() {
        when(productRepository.findById("missing")).thenReturn(null);

        byte[] imageData = new byte[]{1, 2, 3};
        String key = productService.uploadProductImage("missing", imageData, "image/jpeg");

        assertEquals("products/missing/image", key);
        verify(s3StorageService).uploadFile("products/missing/image", imageData, "image/jpeg");
        verify(productRepository, never()).update(any());
    }

    // --- listProductImages ---

    @Test
    public void listProductImagesDelegatesToS3() {
        when(s3StorageService.listFiles("products/p1/")).thenReturn(List.of("products/p1/image"));

        List<String> result = productService.listProductImages("p1");

        assertEquals(1, result.size());
        assertEquals("products/p1/image", result.get(0));
    }
}
