// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;

/**
 * Selects the credentials provider for the Lambda execution environment without
 * walking the full default provider chain during initialization.
 *
 * Lambda supplies the execution role credentials either as environment variables
 * (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN) or through the
 * container credentials endpoint (AWS_CONTAINER_CREDENTIALS_FULL_URI). This uses
 * the endpoint when it is advertised and the environment variables otherwise, so
 * the same code runs on demand and with SnapStart.
 */
public final class LambdaCredentials {

    private LambdaCredentials() {
    }

    public static AwsCredentialsProvider provider() {
        if (System.getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI") != null) {
            return ContainerCredentialsProvider.builder().build();
        }
        return EnvironmentVariableCredentialsProvider.create();
    }
}
