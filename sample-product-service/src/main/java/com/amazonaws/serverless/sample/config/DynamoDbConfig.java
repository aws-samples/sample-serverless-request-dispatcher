// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import javax.inject.Singleton;

/**
 * Configuration class for DynamoDB client setup.
 * Uses environment variable credentials (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 * which are automatically available in Lambda execution environments.
 */
@Singleton
public class DynamoDbConfig {

    public DynamoDbEnhancedClient dynamoDbEnhancedClient() {
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();

        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
