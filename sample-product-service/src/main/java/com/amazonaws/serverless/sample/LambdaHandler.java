// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.Headers;
import com.amazonaws.serverless.sample.config.AppComponent;
import com.amazonaws.serverless.sample.config.DaggerAppComponent;
import com.amazonaws.serverless.requestdispatcher.annotation.RouteException;
import com.amazonaws.serverless.requestdispatcher.annotation.FrontControllerRequestDispatcher;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LambdaHandler implements RequestHandler<AwsProxyRequest, AwsProxyResponse> {

    private static final Logger LOGGER = Logger.getLogger(LambdaHandler.class.getName());
    private static final Gson GSON = new Gson();

    private final FrontControllerRequestDispatcher requestDispatcher;

    public LambdaHandler() {
        // Initialize Dagger component
        AppComponent component = DaggerAppComponent.create();
        this.requestDispatcher = component.requestDispatcher();
    }

    @Override
    public AwsProxyResponse handleRequest(AwsProxyRequest request, Context context) {
        context.getLogger().log("Handling request: " + request.getHttpMethod() + " " + request.getResource());

        try {
            Object result = requestDispatcher.invoke(request);
            return createResponse(200, result.toString());
        } catch (RouteException e) {
            context.getLogger().log("Route error: " + e.getMessage());
            return errorResponse(404, "Not found");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RouteException) {
                context.getLogger().log("Validation error: " + cause.getMessage());
                return errorResponse(400, cause.getMessage());
            }
            LOGGER.log(Level.SEVERE, "Unhandled error in controller", cause);
            return errorResponse(500, "Internal server error");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unhandled error in dispatcher", e);
            return errorResponse(500, "Internal server error");
        }
    }

    // Serialize with Gson so quotes or control characters in a message cannot break the JSON body
    public static AwsProxyResponse errorResponse(int statusCode, String message) {
        return createResponse(statusCode, GSON.toJson(Map.of("error", String.valueOf(message))));
    }

    public static AwsProxyResponse createResponse(int statusCode, String body) {
        Headers headers = new Headers();
        headers.putSingle("Content-Type", "application/json");
        return new AwsProxyResponse(statusCode, headers, body);
    }
}
