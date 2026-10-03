// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.benchmark;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.requestdispatcher.annotation.RouteException;
import com.amazonaws.serverless.sample.LambdaHandler;
import com.amazonaws.serverless.sample.config.DynamoDbConfig;
import com.amazonaws.serverless.sample.config.S3Config;
import com.amazonaws.serverless.sample.controller.ProductController;
import com.amazonaws.serverless.sample.repository.ProductRepository;
import com.amazonaws.serverless.sample.service.ProductService;
import com.amazonaws.serverless.sample.service.S3StorageService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;

import java.util.Map;
import java.util.Objects;

/**
 * Benchmark baseline: the same controller, services, and AWS SDK clients as
 * {@link LambdaHandler}, wired by hand and dispatched with a switch statement
 * instead of the annotation dispatcher and Dagger.
 */
public class PlainHandler implements RequestHandler<AwsProxyRequest, AwsProxyResponse> {

    private final ProductController controller;

    public PlainHandler() {
        String table = Objects.requireNonNull(System.getenv("PRODUCT_TABLE"),
                "PRODUCT_TABLE environment variable must be set");
        String bucket = Objects.requireNonNull(System.getenv("PRODUCT_IMAGES_BUCKET"),
                "PRODUCT_IMAGES_BUCKET environment variable must be set");

        ProductRepository repository = new ProductRepository(new DynamoDbConfig().dynamoDbEnhancedClient(), table);
        S3StorageService storage = new S3StorageService(new S3Config().s3Client(), bucket);
        this.controller = new ProductController(new ProductService(repository, storage), new Gson());
    }

    // For unit tests: bypasses SDK client construction
    PlainHandler(ProductController controller) {
        this.controller = controller;
    }

    @Override
    public AwsProxyResponse handleRequest(AwsProxyRequest request, Context context) {
        try {
            return LambdaHandler.createResponse(200, dispatch(request));
        } catch (RouteException e) {
            return LambdaHandler.errorResponse(400, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return LambdaHandler.errorResponse(404, "Not found");
        } catch (Exception e) {
            return LambdaHandler.errorResponse(500, "Internal server error");
        }
    }

    private String dispatch(AwsProxyRequest request) {
        String route = request.getHttpMethod() + " " + request.getResource();
        switch (route) {
            case "GET /api/products":
                return controller.listProducts(queryParam(request, "category"));
            case "GET /api/products/{id}":
                return controller.getProduct(pathParam(request, "id"));
            case "POST /api/products":
                return controller.createProduct(request.getBody());
            case "PUT /api/products/{id}":
                return controller.updateProduct(pathParam(request, "id"), request.getBody());
            case "DELETE /api/products/{id}":
                return controller.deleteProduct(pathParam(request, "id"));
            default:
                throw new UnsupportedOperationException(route);
        }
    }

    private static String pathParam(AwsProxyRequest request, String name) {
        Map<String, String> params = request.getPathParameters();
        return params == null ? null : params.get(name);
    }

    private static String queryParam(AwsProxyRequest request, String name) {
        return request.getMultiValueQueryStringParameters() == null
                ? null
                : request.getMultiValueQueryStringParameters().getFirst(name);
    }
}
