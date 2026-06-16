// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.repository;

import com.amazonaws.serverless.sample.model.Product;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Product CRUD operations against DynamoDB.
 * Demonstrates DynamoDB Enhanced Client usage with Dagger DI.
 */
@Singleton
public class ProductRepository {

    private final String tableName;
    private final DynamoDbEnhancedClient dynamoDbClient;

    @Inject
    public ProductRepository(DynamoDbEnhancedClient dynamoDbClient,
                             @Named("productTable") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    private DynamoDbTable<Product> getTable() {
        return dynamoDbClient.table(tableName, TableSchema.fromBean(Product.class));
    }

    public List<Product> findAll() {
        List<Product> products = new ArrayList<>();
        getTable().scan(ScanEnhancedRequest.builder().build())
                .forEach(page -> products.addAll(page.items()));
        return products;
    }

    public Product findById(String id) {
        List<Product> results = new ArrayList<>();
        QueryConditional keyCondition = QueryConditional.keyEqualTo(k -> k.partitionValue(id));
        getTable().query(keyCondition).forEach(page -> results.addAll(page.items()));
        return results.isEmpty() ? null : results.get(0);
    }

    public Product create(Product product) {
        if (product.getId() == null) {
            product.setId(UUID.randomUUID().toString());
        }
        getTable().putItem(product);
        return product;
    }

    public Product update(Product product) {
        Product existing = findById(product.getId());
        if (existing == null) {
            return null;
        }
        getTable().putItem(product);
        return product;
    }

    public void delete(String id) {
        Product product = findById(id);
        if (product != null) {
            getTable().deleteItem(product);
        }
    }
}
