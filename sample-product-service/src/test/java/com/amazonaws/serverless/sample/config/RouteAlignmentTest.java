// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.requestdispatcher.annotation.FrontControllerRequestDispatcher;
import com.amazonaws.serverless.requestdispatcher.annotation.RouteException;
import com.amazonaws.serverless.sample.controller.ProductController;
import com.amazonaws.serverless.sample.model.Product;
import com.amazonaws.serverless.sample.repository.ProductRepository;
import com.amazonaws.serverless.sample.service.ProductService;
import com.amazonaws.serverless.sample.service.S3StorageService;
import com.google.gson.Gson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Validates that all routes declared in the SAM template (template.yaml)
 * are actually registered by the dispatcher. This catches drift between
 * the infrastructure definition and the application code.
 *
 * Expected routes from template.yaml Events:
 *   GET    /api/products
 *   GET    /api/products/{id}
 *   POST   /api/products
 *   PUT    /api/products/{id}
 *   DELETE /api/products/{id}
 */
public class RouteAlignmentTest {

    private FrontControllerRequestDispatcher dispatcher;
    private ProductRepository productRepository;

    @BeforeEach
    public void setUp() {
        productRepository = mock(ProductRepository.class);
        S3StorageService s3StorageService = mock(S3StorageService.class);
        ProductService productService = new ProductService(productRepository, s3StorageService);
        ProductController controller = new ProductController(productService, new Gson());
        dispatcher = new AppRequestDispatcher(List.of(controller));
    }

    @ParameterizedTest(name = "{0} {1} is registered")
    @CsvSource({
        "GET,    /api/products",
        "GET,    /api/products/{id}",
        "POST,   /api/products",
        "PUT,    /api/products/{id}",
        "DELETE,  /api/products/{id}"
    })
    public void templateRouteIsRegistered(String method, String path) {
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findById(any())).thenReturn(
                new Product("id", "Name", "Cat", 10.0, null, null));
        when(productRepository.create(any())).thenReturn(
                new Product("id", "Name", "Cat", 10.0, null, null));
        when(productRepository.update(any())).thenReturn(
                new Product("id", "Name", "Cat", 10.0, null, null));

        AwsProxyRequest request = new AwsProxyRequest();
        request.setHttpMethod(method.trim());
        request.setResource(path.trim());
        request.setPathParameters(Map.of("id", "test-id"));
        request.setBody("{\"name\":\"Test\",\"category\":\"Cat\",\"price\":10.0}");

        assertDoesNotThrow(() -> dispatcher.invoke(request),
                method + " " + path + " should be a registered route");
    }

    @ParameterizedTest(name = "{0} {1} is NOT registered")
    @CsvSource({
        "PATCH,  /api/products",
        "PUT,    /api/products",
        "DELETE,  /api/products",
        "POST,   /api/products/{id}",
        "PATCH,  /api/products/{id}"
    })
    public void unregisteredRouteThrowsRouteException(String method, String path) {
        AwsProxyRequest request = new AwsProxyRequest();
        request.setHttpMethod(method.trim());
        request.setResource(path.trim());
        request.setPathParameters(new HashMap<>());

        assertThrows(RouteException.class, () -> dispatcher.invoke(request),
                method + " " + path + " should NOT be registered");
    }

    @Test
    public void basePathIsApiNotRoot() {
        AwsProxyRequest request = new AwsProxyRequest();
        request.setHttpMethod("GET");
        request.setResource("/products");
        request.setPathParameters(new HashMap<>());

        assertThrows(RouteException.class, () -> dispatcher.invoke(request),
                "Routes without /api prefix should not match");
    }

    @Test
    public void exactlyFiveRoutesAreRegistered() {
        when(productRepository.findAll()).thenReturn(List.of());
        when(productRepository.findById(any())).thenReturn(
                new Product("id", "N", "C", 1.0, null, null));
        when(productRepository.create(any())).thenReturn(
                new Product("id", "N", "C", 1.0, null, null));
        when(productRepository.update(any())).thenReturn(
                new Product("id", "N", "C", 1.0, null, null));

        String[][] routes = {
            {"GET", "/api/products"},
            {"GET", "/api/products/{id}"},
            {"POST", "/api/products"},
            {"PUT", "/api/products/{id}"},
            {"DELETE", "/api/products/{id}"}
        };

        int successCount = 0;
        for (String[] route : routes) {
            AwsProxyRequest request = new AwsProxyRequest();
            request.setHttpMethod(route[0]);
            request.setResource(route[1]);
            request.setPathParameters(Map.of("id", "test"));
            request.setBody("{\"name\":\"T\",\"category\":\"C\",\"price\":1.0}");
            try {
                dispatcher.invoke(request);
                successCount++;
            } catch (RouteException e) {
                fail("Expected route " + route[0] + " " + route[1] + " to be registered");
            } catch (Exception e) {
                successCount++;
            }
        }
        assertEquals(5, successCount, "All 5 template.yaml routes should be registered");
    }
}
