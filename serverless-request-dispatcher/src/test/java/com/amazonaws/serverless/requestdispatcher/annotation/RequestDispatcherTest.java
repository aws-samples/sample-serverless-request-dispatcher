// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.requestdispatcher.annotation;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.Headers;
import com.amazonaws.serverless.proxy.model.MultiValuedTreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RequestDispatcherTest {

    private FrontControllerRequestDispatcher dispatcher;

    @BeforeEach
    public void setUp() {
        dispatcher = new TestRequestDispatcher(List.of(new TestController()));
    }

    // ---- GET routes ----

    @Test
    public void testGetRouteNoParams() throws Exception {
        AwsProxyRequest request = buildRequest("GET", "/items");
        Object result = dispatcher.invoke(request);
        assertEquals("[\"item1\",\"item2\"]", result);
    }

    @Test
    public void testGetRouteWithPathParam() throws Exception {
        AwsProxyRequest request = buildRequest("GET", "/items/{id}");
        request.setPathParameters(Map.of("id", "42"));
        Object result = dispatcher.invoke(request);
        assertEquals("{\"id\":\"42\"}", result);
    }

    @Test
    public void testGetRouteWithRequestParam() throws Exception {
        AwsProxyRequest request = buildRequest("GET", "/search");
        MultiValuedTreeMap<String, String> queryParams = new MultiValuedTreeMap<>();
        queryParams.add("q", "hello");
        request.setMultiValueQueryStringParameters(queryParams);
        Object result = dispatcher.invoke(request);
        assertEquals("{\"query\":\"hello\"}", result);
    }

    @Test
    public void testGetRouteWithNullQueryString() throws Exception {
        AwsProxyRequest request = buildRequest("GET", "/search");
        // No query params set — should not throw NPE
        Object result = dispatcher.invoke(request);
        assertEquals("{\"query\":\"null\"}", result);
    }

    @Test
    public void testGetRouteInjectsAwsProxyRequest() throws Exception {
        AwsProxyRequest request = buildRequest("GET", "/echo");
        Object result = dispatcher.invoke(request);
        assertEquals("GET /echo", result);
    }

    @Test
    public void testGetRouteInjectsHeaders() throws Exception {
        AwsProxyRequest request = buildRequest("GET", "/headers");
        Headers headers = new Headers();
        headers.putSingle("X-Custom", "test-value");
        request.setMultiValueHeaders(headers);
        Object result = dispatcher.invoke(request);
        assertNotNull(result);
        assertTrue(result.toString().contains("X-Custom"));
    }

    // ---- POST route ----

    @Test
    public void testPostRoute() throws Exception {
        AwsProxyRequest request = buildRequest("POST", "/items");
        request.setBody("{\"name\":\"widget\"}");
        Object result = dispatcher.invoke(request);
        assertEquals("{\"created\":{\"name\":\"widget\"}}", result);
    }

    // ---- PUT route ----

    @Test
    public void testPutRouteWithPathParamAndBody() throws Exception {
        AwsProxyRequest request = buildRequest("PUT", "/items/{id}");
        request.setPathParameters(Map.of("id", "7"));
        request.setBody("{\"name\":\"updated\"}");
        Object result = dispatcher.invoke(request);
        assertEquals("{\"id\":\"7\",\"updated\":{\"name\":\"updated\"}}", result);
    }

    // ---- DELETE route ----

    @Test
    public void testDeleteRoute() throws Exception {
        AwsProxyRequest request = buildRequest("DELETE", "/items/{id}");
        request.setPathParameters(Map.of("id", "99"));
        Object result = dispatcher.invoke(request);
        assertEquals("{\"deleted\":\"99\"}", result);
    }

    // ---- PATCH route ----

    @Test
    public void testPatchRoute() throws Exception {
        AwsProxyRequest request = buildRequest("PATCH", "/items/{id}");
        request.setPathParameters(Map.of("id", "5"));
        request.setBody("{\"status\":\"active\"}");
        Object result = dispatcher.invoke(request);
        assertEquals("{\"id\":\"5\",\"patched\":{\"status\":\"active\"}}", result);
    }

    // ---- Error cases ----

    @Test
    public void testUnknownRouteThrowsRouteException() {
        AwsProxyRequest request = buildRequest("GET", "/nonexistent");
        assertThrows(RouteException.class, () -> dispatcher.invoke(request));
    }

    @Test
    public void testWrongHttpMethodThrowsRouteException() {
        // /items is registered for GET and POST, not DELETE
        AwsProxyRequest request = buildRequest("DELETE", "/items");
        assertThrows(RouteException.class, () -> dispatcher.invoke(request));
    }

    // ---- Duplicate route detection ----

    @Test
    public void testDuplicateRouteThrowsAtConstruction() {
        assertThrows(RouteException.class,
                () -> new TestRequestDispatcher(List.of(new TestController(), new DuplicateController())));
    }

    // ---- Controller deduplication ----

    @Test
    public void testSameControllerClassPassedTwiceThrowsDuplicate() {
        // The framework detects duplicate routes even from the same controller instance.
        // Passing the same controller twice is a configuration error.
        TestController controller = new TestController();
        RouteException e = assertThrows(RouteException.class,
                () -> new TestRequestDispatcher(List.of(controller, controller)));
        assertTrue(e.getMessage().contains("Duplicate route found for path"));
    }

    // ---- Helpers ----

    private AwsProxyRequest buildRequest(String httpMethod, String resource) {
        AwsProxyRequest request = new AwsProxyRequest();
        request.setHttpMethod(httpMethod);
        request.setResource(resource);
        request.setPathParameters(new HashMap<>());
        return request;
    }

    /**
     * Controller that declares a route conflicting with TestController.
     */
    public static class DuplicateController {
        @GetMapping("/items")
        public String duplicateList() {
            return "duplicate";
        }
    }
}
