// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.benchmark;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.requestdispatcher.annotation.FrontControllerRequestDispatcher;
import com.amazonaws.serverless.requestdispatcher.annotation.RouteException;
import com.amazonaws.serverless.sample.LambdaHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

/**
 * Benchmark variant: dispatcher and Dagger, no AWS SDK clients.
 */
public class MinimalHandler implements RequestHandler<AwsProxyRequest, AwsProxyResponse> {

    private final FrontControllerRequestDispatcher requestDispatcher;

    public MinimalHandler() {
        this.requestDispatcher = DaggerMinimalComponent.create().requestDispatcher();
    }

    @Override
    public AwsProxyResponse handleRequest(AwsProxyRequest request, Context context) {
        try {
            return LambdaHandler.createResponse(200, requestDispatcher.invoke(request).toString());
        } catch (RouteException e) {
            return LambdaHandler.errorResponse(404, "Not found");
        } catch (Exception e) {
            return LambdaHandler.errorResponse(500, "Internal server error");
        }
    }
}
