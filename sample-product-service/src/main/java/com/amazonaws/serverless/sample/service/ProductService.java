// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.service;

import com.amazonaws.serverless.sample.model.Product;
import com.amazonaws.serverless.sample.repository.ProductRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer for Product operations.
 * Delegates persistence to ProductRepository (DynamoDB) and
 * file storage to S3StorageService.
 */
@Singleton
public class ProductService {

    private final ProductRepository productRepository;
    private final S3StorageService s3StorageService;

    @Inject
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Dagger-managed singletons injected via constructor DI")
    public ProductService(ProductRepository productRepository, S3StorageService s3StorageService) {
        this.productRepository = productRepository;
        this.s3StorageService = s3StorageService;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public List<Product> findByCategory(String category) {
        return productRepository.findAll().stream()
                .filter(p -> category.equalsIgnoreCase(p.getCategory()))
                .collect(Collectors.toList());
    }

    public Product findById(String id) {
        return productRepository.findById(id);
    }

    public Product create(Product product) {
        return productRepository.create(product);
    }

    public Product update(Product product) {
        return productRepository.update(product);
    }

    public void delete(String id) {
        Product product = productRepository.findById(id);
        if (product != null && product.getImageKey() != null) {
            s3StorageService.deleteFile(product.getImageKey());
        }
        productRepository.delete(id);
    }

    public String uploadProductImage(String productId, byte[] imageData, String contentType) {
        String key = "products/" + productId + "/image";
        s3StorageService.uploadFile(key, imageData, contentType);

        Product product = productRepository.findById(productId);
        if (product != null) {
            product.setImageKey(key);
            productRepository.update(product);
        }
        return key;
    }

    public List<String> listProductImages(String productId) {
        return s3StorageService.listFiles("products/" + productId + "/");
    }
}
