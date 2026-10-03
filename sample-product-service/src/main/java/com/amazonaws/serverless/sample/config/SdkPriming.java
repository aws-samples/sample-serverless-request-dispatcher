// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import software.amazon.awssdk.core.warmup.SdkWarmUp;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Optionally exercises the AWS SDK request path for the clients this function uses
 * during the Lambda initialization phase, using canned responses and no network calls.
 *
 * Without it, the first request in each new execution environment pays for loading
 * and initializing the SDK's marshalling and request-pipeline classes. With priming,
 * that work moves into initialization; with SnapStart it is captured in the snapshot,
 * so restored environments don't repeat it.
 *
 * Enabled when the SDK_PRIMING environment variable is "true".
 */
public final class SdkPriming {

    private static volatile boolean primed;

    private SdkPriming() {
    }

    public static void prime() {
        prime(Boolean.parseBoolean(System.getenv("SDK_PRIMING")));
    }

    static boolean prime(boolean enabled) {
        if (enabled && !primed) {
            SdkWarmUp.warmUp(DynamoDbClient.class, S3Client.class);
            primed = true;
        }
        return primed;
    }
}
