// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.requestdispatcher.annotation.FrontControllerRequestDispatcher;
import com.amazonaws.serverless.requestdispatcher.annotation.RouteException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LambdaHandlerTest {

    private LambdaHandler handler;
    private FrontControllerRequestDispatcher dispatcher;
    private Context context;

    @BeforeEach
    public void setUp() throws Exception {
        dispatcher = mock(FrontControllerRequestDispatcher.class);

        // mock with CALLS_REAL_METHODS skips the constructor (no Dagger / env vars needed)
        handler = mock(LambdaHandler.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        Field field = LambdaHandler.class.getDeclaredField("requestDispatcher");
        field.setAccessible(true);
        field.set(handler, dispatcher);

        context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);
        when(context.getLogger()).thenReturn(logger);
    }

    private AwsProxyRequest buildRequest(String method, String resource) {
        AwsProxyRequest request = new AwsProxyRequest();
        request.setHttpMethod(method);
        request.setResource(resource);
        return request;
    }

    // --- Success cases ---

    @Test
    public void successfulRequestReturns200() throws Exception {
        AwsProxyRequest request = buildRequest("GET", "/api/products");
        when(dispatcher.invoke(request)).thenReturn("[{\"id\":\"1\"}]");

        AwsProxyResponse response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
        assertEquals("[{\"id\":\"1\"}]", response.getBody());
    }

    @Test
    public void responseHasJsonContentType() throws Exception {
        AwsProxyRequest request = buildRequest("GET", "/api/products");
        when(dispatcher.invoke(request)).thenReturn("[]");

        AwsProxyResponse response = handler.handleRequest(request, context);

        assertEquals("application/json",
                response.getMultiValueHeaders().getFirst("Content-Type"));
    }

    // --- 404 RouteException (route not found) ---

    @Test
    public void routeExceptionReturns404() throws Exception {
        AwsProxyRequest request = buildRequest("GET", "/api/unknown");
        when(dispatcher.invoke(request)).thenThrow(new RouteException("Not found"));

        AwsProxyResponse response = handler.handleRequest(request, context);

        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Not found"));
    }

    // --- 400 InvocationTargetException wrapping RouteException (validation error) ---

    @Test
    public void invocationTargetExceptionWithRouteExceptionCauseReturns400() throws Exception {
        AwsProxyRequest request = buildRequest("POST", "/api/products");
        RouteException cause = new RouteException("Product name is required");
        when(dispatcher.invoke(request))
                .thenThrow(new InvocationTargetException(cause));

        AwsProxyResponse response = handler.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Product name is required"));
    }

    // --- 500 InvocationTargetException with non-RouteException cause ---

    @Test
    public void invocationTargetExceptionWithOtherCauseReturns500() throws Exception {
        AwsProxyRequest request = buildRequest("GET", "/api/products");
        when(dispatcher.invoke(request))
                .thenThrow(new InvocationTargetException(new NullPointerException("oops")));

        AwsProxyResponse response = handler.handleRequest(request, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Internal server error"));
    }

    // --- 500 generic Exception ---

    @Test
    public void genericExceptionReturns500() throws Exception {
        AwsProxyRequest request = buildRequest("GET", "/api/products");
        when(dispatcher.invoke(request))
                .thenThrow(new IllegalAccessException("access denied"));

        AwsProxyResponse response = handler.handleRequest(request, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Internal server error"));
    }

    // --- Error bodies are valid JSON even when the message contains quotes ---

    @Test
    public void testErrorMessageIsJsonEscaped() {
        AwsProxyResponse response = LambdaHandler.errorResponse(400, "name \"x\" is invalid\n");
        com.google.gson.JsonObject body = com.google.gson.JsonParser.parseString(response.getBody()).getAsJsonObject();
        assertEquals("name \"x\" is invalid\n", body.get("error").getAsString());
    }
}
