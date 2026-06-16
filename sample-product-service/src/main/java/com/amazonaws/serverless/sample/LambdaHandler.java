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

import java.lang.reflect.InvocationTargetException;

public class LambdaHandler implements RequestHandler<AwsProxyRequest, AwsProxyResponse> {

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
            return createResponse(404, "{\"error\": \"Not found\"}");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RouteException) {
                context.getLogger().log("Validation error: " + cause.getMessage());
                return createResponse(400, "{\"error\": \"" + cause.getMessage() + "\"}");
            }
            context.getLogger().log("Error: " + cause.getMessage());
            cause.printStackTrace();
            return createResponse(500, "{\"error\": \"Internal server error\"}");
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, "{\"error\": \"Internal server error\"}");
        }
    }

    private AwsProxyResponse createResponse(int statusCode, String body) {
        Headers headers = new Headers();
        headers.putSingle("Content-Type", "application/json");
        return new AwsProxyResponse(statusCode, headers, body);
    }
}
