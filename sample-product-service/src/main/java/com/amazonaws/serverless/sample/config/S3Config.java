// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import software.amazon.awssdk.services.s3.S3Client;

import javax.inject.Singleton;

/**
 * Configuration class for S3 client setup.
 * Uses the execution role credentials supplied by Lambda; see {@link LambdaCredentials}.
 */
@Singleton
public class S3Config {

    public S3Client s3Client() {
        return S3Client.builder()
                .credentialsProvider(LambdaCredentials.provider())
                .build();
    }
}
