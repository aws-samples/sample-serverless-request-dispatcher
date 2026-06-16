// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.integration;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.MultiValuedTreeMap;
import com.amazonaws.serverless.requestdispatcher.annotation.FrontControllerRequestDispatcher;
import com.amazonaws.serverless.requestdispatcher.annotation.RouteException;
import com.amazonaws.serverless.sample.config.AppRequestDispatcher;
import com.amazonaws.serverless.sample.controller.ProductController;
import com.amazonaws.serverless.sample.model.Product;
import com.amazonaws.serverless.sample.repository.ProductRepository;
import com.amazonaws.serverless.sample.service.ProductService;
import com.amazonaws.serverless.sample.service.S3StorageService;
import com.google.gson.Gson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test that wires the real dispatcher (with basePath "/api")
 * to the real ProductController, verifying that:
 * - basePath is correctly prepended to annotation paths
 * - Route matching works against API Gateway resource templates
 * - Path variables, request params, and body are correctly extracted
 * - Error semantics match the README documentation
 */
public class RequestDispatcherIntegrationTest {

    private ProductRepository productRepository;
    private S3StorageService s3StorageService;
    private FrontControllerRequestDispatcher dispatcher;
    private Gson gson;

    @BeforeEach
    public void setUp() {
        productRepository = mock(ProductRepository.class);
        s3StorageService = mock(S3StorageService.class);
        gson = new Gson();

        ProductService productService = new ProductService(productRepository, s3StorageService);
        ProductController controller = new ProductController(productService, gson);
        dispatcher = new AppRequestDispatcher(List.of(controller));
    }

    private AwsProxyRequest buildRequest(String httpMethod, String resource) {
        AwsProxyRequest request = new AwsProxyRequest();
        request.setHttpMethod(httpMethod);
        request.setResource(resource);
        request.setPathParameters(new HashMap<>());
        return request;
    }

    // --- basePath integration: routes are registered at /api/products ---

    @Test
    public void getProductsRouteIsRegisteredWithBasePath() throws Exception {
        when(productRepository.findAll()).thenReturn(List.of());

        AwsProxyRequest request = buildRequest("GET", "/api/products");
        Object result = dispatcher.invoke(request);

        assertEquals("[]", result.toString());
    }

    @Test
    public void routeWithoutBasePathThrowsRouteException() {
        AwsProxyRequest request = buildRequest("GET", "/products");

        assertThrows(RouteException.class, () -> dispatcher.invoke(request));
    }

    // --- GET /api/products ---

    @Test
    public void listProductsWithoutQueryParam() throws Exception {
        Product p = new Product("1", "Widget", "Hardware", 10.0, null, null);
        when(productRepository.findAll()).thenReturn(List.of(p));

        AwsProxyRequest request = buildRequest("GET", "/api/products");
        Object result = dispatcher.invoke(request);

        assertTrue(result.toString().contains("Widget"));
    }

    @Test
    public void listProductsWithCategoryQueryParam() throws Exception {
        Product elec = new Product("1", "Phone", "Electronics", 500.0, null, null);
        Product hw = new Product("2", "Hammer", "Hardware", 15.0, null, null);
        when(productRepository.findAll()).thenReturn(List.of(elec, hw));

        AwsProxyRequest request = buildRequest("GET", "/api/products");
        MultiValuedTreeMap<String, String> queryParams = new MultiValuedTreeMap<>();
        queryParams.add("category", "Electronics");
        request.setMultiValueQueryStringParameters(queryParams);

        Object result = dispatcher.invoke(request);

        assertTrue(result.toString().contains("Phone"));
        assertFalse(result.toString().contains("Hammer"));
    }

    // --- GET /api/products/{id} ---

    @Test
    public void getProductByIdExtractsPathVariable() throws Exception {
        Product p = new Product("abc-123", "Laptop", "Electronics", 999.0, null, null);
        when(productRepository.findById("abc-123")).thenReturn(p);

        AwsProxyRequest request = buildRequest("GET", "/api/products/{id}");
        request.setPathParameters(Map.of("id", "abc-123"));

        Object result = dispatcher.invoke(request);

        assertTrue(result.toString().contains("abc-123"));
        assertTrue(result.toString().contains("Laptop"));
    }

    @Test
    public void getProductNotFoundThrowsWrappedRouteException() {
        when(productRepository.findById("missing")).thenReturn(null);

        AwsProxyRequest request = buildRequest("GET", "/api/products/{id}");
        request.setPathParameters(Map.of("id", "missing"));

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> dispatcher.invoke(request));
        assertInstanceOf(RouteException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Product not found"));
    }

    // --- POST /api/products ---

    @Test
    public void createProductWithBody() throws Exception {
        Product created = new Product("new-id", "Keyboard", "Electronics", 79.99, null, null);
        when(productRepository.create(any(Product.class))).thenReturn(created);

        AwsProxyRequest request = buildRequest("POST", "/api/products");
        request.setBody("{\"name\":\"Keyboard\",\"category\":\"Electronics\",\"price\":79.99}");

        Object result = dispatcher.invoke(request);

        assertTrue(result.toString().contains("new-id"));
        assertTrue(result.toString().contains("Keyboard"));
    }

    @Test
    public void createProductValidationErrorThrowsWrappedRouteException() {
        AwsProxyRequest request = buildRequest("POST", "/api/products");
        request.setBody("{\"name\":\"\"}");

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> dispatcher.invoke(request));
        assertInstanceOf(RouteException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Product name is required"));
    }

    // --- PUT /api/products/{id} ---

    @Test
    public void updateProductExtractsPathVariableAndBody() throws Exception {
        Product updated = new Product("u-id", "Updated", "Cat", 50.0, null, null);
        when(productRepository.update(any(Product.class))).thenReturn(updated);

        AwsProxyRequest request = buildRequest("PUT", "/api/products/{id}");
        request.setPathParameters(Map.of("id", "u-id"));
        request.setBody("{\"name\":\"Updated\",\"category\":\"Cat\",\"price\":50.0}");

        Object result = dispatcher.invoke(request);

        assertTrue(result.toString().contains("Updated"));
        verify(productRepository).update(argThat(p -> "u-id".equals(p.getId())));
    }

    // --- DELETE /api/products/{id} ---

    @Test
    public void deleteProductExtractsPathVariable() throws Exception {
        Product p = new Product("d-id", "ToDelete", "Cat", 10.0, null, null);
        when(productRepository.findById("d-id")).thenReturn(p);

        AwsProxyRequest request = buildRequest("DELETE", "/api/products/{id}");
        request.setPathParameters(Map.of("id", "d-id"));

        Object result = dispatcher.invoke(request);

        assertTrue(result.toString().contains("Product deleted"));
        verify(productRepository).delete("d-id");
    }

    // --- Wrong HTTP method returns RouteException ---

    @Test
    public void patchOnProductsThrowsRouteException() {
        AwsProxyRequest request = buildRequest("PATCH", "/api/products");

        assertThrows(RouteException.class, () -> dispatcher.invoke(request));
    }

    // --- Duplicate route detection ---

    @Test
    public void duplicateControllerThrowsRouteExceptionAtConstruction() {
        ProductService service = new ProductService(productRepository, s3StorageService);
        ProductController controller1 = new ProductController(service, gson);
        ProductController controller2 = new ProductController(service, gson);

        RouteException ex = assertThrows(RouteException.class,
                () -> new AppRequestDispatcher(List.of(controller1, controller2)));
        assertTrue(ex.getMessage().contains("Duplicate route found for path"));
    }

    // --- Null basePath handled gracefully ---

    @Test
    public void nullBasePathDefaultsToEmpty() throws Exception {
        ProductService service = new ProductService(productRepository, s3StorageService);
        ProductController controller = new ProductController(service, gson);

        FrontControllerRequestDispatcher nullBaseDispatcher =
                new FrontControllerRequestDispatcher(List.of(controller), null) {};

        when(productRepository.findAll()).thenReturn(List.of());

        AwsProxyRequest request = buildRequest("GET", "/products");
        Object result = nullBaseDispatcher.invoke(request);
        assertEquals("[]", result.toString());
    }
}
