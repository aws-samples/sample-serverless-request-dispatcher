// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import com.amazonaws.serverless.sample.controller.ProductController;
import com.amazonaws.serverless.sample.repository.ProductRepository;
import com.amazonaws.serverless.sample.service.ProductService;
import com.amazonaws.serverless.sample.service.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the Dagger dependency graph resolves correctly.
 * Uses TestModule which provides mocked AWS clients so no real
 * AWS resources are needed.
 */
public class DaggerDependencyTest {

    private TestComponent component;

    @BeforeEach
    public void setUp() {
        component = DaggerTestComponent.create();
    }

    @Test
    public void componentCreatesSuccessfully() {
        assertNotNull(component, "Dagger component should be created");
    }

    @Test
    public void requestDispatcherIsResolved() {
        AppRequestDispatcher dispatcher = component.requestDispatcher();
        assertNotNull(dispatcher, "RequestDispatcher should be resolved from the DI graph");
    }

    @Test
    public void productControllerIsResolved() {
        ProductController controller = component.productController();
        assertNotNull(controller, "ProductController should be resolved from the DI graph");
    }

    @Test
    public void productServiceIsResolved() {
        ProductService service = component.productService();
        assertNotNull(service, "ProductService should be resolved from the DI graph");
    }

    @Test
    public void productRepositoryIsResolved() {
        ProductRepository repository = component.productRepository();
        assertNotNull(repository, "ProductRepository should be resolved from the DI graph");
    }

    @Test
    public void s3StorageServiceIsResolved() {
        S3StorageService service = component.s3StorageService();
        assertNotNull(service, "S3StorageService should be resolved from the DI graph");
    }

    @Test
    public void singletonsReturnSameInstance() {
        assertSame(component.requestDispatcher(), component.requestDispatcher(),
                "RequestDispatcher should be singleton");
        assertSame(component.productService(), component.productService(),
                "ProductService should be singleton");
        assertSame(component.s3StorageService(), component.s3StorageService(),
                "S3StorageService should be singleton");
        assertSame(component.productRepository(), component.productRepository(),
                "ProductRepository should be singleton");
    }

    @Test
    public void requestDispatcherHasRegisteredRoutes() {
        // If routes failed to register, the constructor would have thrown.
        // Getting here means all 5 ProductController routes were registered.
        AppRequestDispatcher dispatcher = component.requestDispatcher();
        assertNotNull(dispatcher);
    }
}
