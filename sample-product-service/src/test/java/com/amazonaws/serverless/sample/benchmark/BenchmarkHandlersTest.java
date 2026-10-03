// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.benchmark;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.requestdispatcher.annotation.RouteException;
import com.amazonaws.serverless.sample.controller.ProductController;
import com.amazonaws.services.lambda.runtime.Context;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class BenchmarkHandlersTest {

    private final Context context = mock(Context.class);

    private static AwsProxyRequest request(String method, String resource) {
        AwsProxyRequest request = new AwsProxyRequest();
        request.setHttpMethod(method);
        request.setResource(resource);
        request.setPath(resource);
        return request;
    }

    // --- MinimalHandler: real Dagger graph, no AWS clients ---

    @Test
    public void minimalHandlerServesBenchmarkRoute() {
        AwsProxyResponse response = new MinimalHandler().handleRequest(request("GET", "/api/products"), context);
        assertEquals(200, response.getStatusCode());
        assertEquals("[]", response.getBody());
    }

    @Test
    public void minimalHandlerReturns404ForUnknownRoute() {
        AwsProxyResponse response = new MinimalHandler().handleRequest(request("DELETE", "/api/products"), context);
        assertEquals(404, response.getStatusCode());
    }

    // --- PlainHandler: switch dispatch over the real controller ---

    @Test
    public void plainHandlerRoutesListToController() {
        ProductController controller = mock(ProductController.class);
        when(controller.listProducts(null)).thenReturn("[{\"id\":\"1\"}]");

        AwsProxyResponse response = new PlainHandler(controller).handleRequest(request("GET", "/api/products"), context);

        assertEquals(200, response.getStatusCode());
        assertEquals("[{\"id\":\"1\"}]", response.getBody());
    }

    @Test
    public void plainHandlerPassesPathParameter() {
        ProductController controller = mock(ProductController.class);
        when(controller.getProduct("42")).thenReturn("{\"id\":\"42\"}");
        AwsProxyRequest request = request("GET", "/api/products/{id}");
        request.setPathParameters(Map.of("id", "42"));

        AwsProxyResponse response = new PlainHandler(controller).handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
        verify(controller).getProduct("42");
    }

    @Test
    public void plainHandlerMapsValidationErrorTo400() {
        ProductController controller = mock(ProductController.class);
        when(controller.createProduct(any())).thenThrow(new RouteException("Product name is required"));

        AwsProxyResponse response = new PlainHandler(controller).handleRequest(request("POST", "/api/products"), context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Product name is required"));
    }

    @Test
    public void plainHandlerReturns404ForUnknownRoute() {
        AwsProxyResponse response = new PlainHandler(mock(ProductController.class))
                .handleRequest(request("PATCH", "/api/products"), context);
        assertEquals(404, response.getStatusCode());
    }
}
