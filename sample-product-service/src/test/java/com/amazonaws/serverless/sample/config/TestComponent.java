// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import com.amazonaws.serverless.sample.controller.ProductController;
import com.amazonaws.serverless.sample.repository.ProductRepository;
import com.amazonaws.serverless.sample.service.ProductService;
import com.amazonaws.serverless.sample.service.S3StorageService;
import dagger.Component;

import javax.inject.Singleton;

/**
 * Test Dagger component wired with TestModule (mocked AWS clients).
 */
@Component(modules = {TestModule.class})
@Singleton
public interface TestComponent {
    AppRequestDispatcher requestDispatcher();
    ProductController productController();
    ProductService productService();
    ProductRepository productRepository();
    S3StorageService s3StorageService();
}
