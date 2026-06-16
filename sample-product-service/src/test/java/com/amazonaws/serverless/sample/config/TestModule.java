// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import com.amazonaws.serverless.sample.controller.ProductController;
import com.google.gson.Gson;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.s3.S3Client;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Test Dagger module that substitutes mocked AWS clients
 * so the full DI graph can be validated without real AWS resources.
 */
@Module
public class TestModule {

    @Provides
    @Singleton
    public Gson provideGson() {
        return new Gson();
    }

    @Provides
    @Singleton
    public DynamoDbEnhancedClient provideDynamoDbEnhancedClient() {
        return mock(DynamoDbEnhancedClient.class);
    }

    @Provides
    @Singleton
    public S3Client provideS3Client() {
        return mock(S3Client.class);
    }

    @Provides
    @Singleton
    @Named("productTable")
    public String provideProductTableName() {
        return "test-products";
    }

    @Provides
    @Singleton
    @Named("productImagesBucket")
    public String provideProductImagesBucket() {
        return "test-product-images";
    }

    @Provides
    @Singleton
    public List<Object> provideControllers(ProductController productController) {
        return List.of(productController);
    }
}
