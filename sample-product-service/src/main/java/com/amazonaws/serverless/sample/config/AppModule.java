// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import com.amazonaws.serverless.sample.controller.ProductController;
import com.amazonaws.serverless.sample.repository.ProductRepository;
import com.amazonaws.serverless.sample.service.ProductService;
import com.amazonaws.serverless.sample.service.S3StorageService;
import com.google.gson.Gson;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.s3.S3Client;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;

/**
 * Dagger module that wires up all dependencies including AWS service clients.
 *
 * Dependency graph:
 *   DynamoDbConfig  -> DynamoDbEnhancedClient -> ProductRepository
 *   S3Config        -> S3Client               -> S3StorageService
 *   ProductRepository + S3StorageService       -> ProductService
 *   ProductService + Gson                      -> ProductController
 *   ProductController                          -> AppRequestDispatcher
 */
@Module
public class AppModule {

    @Provides
    @Singleton
    public Gson provideGson() {
        return new Gson();
    }

    @Provides
    @Singleton
    public DynamoDbEnhancedClient provideDynamoDbEnhancedClient() {
        return new DynamoDbConfig().dynamoDbEnhancedClient();
    }

    @Provides
    @Singleton
    public S3Client provideS3Client() {
        return new S3Config().s3Client();
    }

    @Provides
    @Singleton
    @Named("productTable")
    public String provideProductTableName() {
        return Objects.requireNonNull(System.getenv("PRODUCT_TABLE"),
                "PRODUCT_TABLE environment variable must be set");
    }

    @Provides
    @Singleton
    @Named("productImagesBucket")
    public String provideProductImagesBucket() {
        return Objects.requireNonNull(System.getenv("PRODUCT_IMAGES_BUCKET"),
                "PRODUCT_IMAGES_BUCKET environment variable must be set");
    }

    @Provides
    @Singleton
    public ProductRepository provideProductRepository(DynamoDbEnhancedClient dynamoDbClient,
                                                      @Named("productTable") String tableName) {
        return new ProductRepository(dynamoDbClient, tableName);
    }

    @Provides
    @Singleton
    public S3StorageService provideS3StorageService(S3Client s3Client,
                                                    @Named("productImagesBucket") String bucketName) {
        return new S3StorageService(s3Client, bucketName);
    }

    @Provides
    @Singleton
    public ProductService provideProductService(ProductRepository repository, S3StorageService s3Service) {
        return new ProductService(repository, s3Service);
    }

    @Provides
    @Singleton
    public List<Object> provideControllers(ProductController productController) {
        return List.of(productController);
    }
}
