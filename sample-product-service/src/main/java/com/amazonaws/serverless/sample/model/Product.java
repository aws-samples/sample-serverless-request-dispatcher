// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Product model annotated for DynamoDB Enhanced Client mapping.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Product {
    private String id;
    private String name;
    private String category;
    private Double price;
    private String description;
    private String imageKey;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    public String getId() {
        return id;
    }

    @DynamoDbAttribute("Name")
    public String getName() {
        return name;
    }

    @DynamoDbAttribute("Category")
    public String getCategory() {
        return category;
    }

    @DynamoDbAttribute("Price")
    public Double getPrice() {
        return price;
    }

    @DynamoDbAttribute("Description")
    public String getDescription() {
        return description;
    }

    @DynamoDbAttribute("ImageKey")
    public String getImageKey() {
        return imageKey;
    }
}
