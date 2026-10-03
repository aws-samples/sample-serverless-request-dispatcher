// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SdkPrimingTest {

    @Test
    public void disabledPrimingDoesNothingAndEnabledPrimingRunsOfflineOnce() {
        assertFalse(SdkPriming.prime(false), "priming must be a no-op when disabled");
        // Uses canned responses: no credentials, Region endpoint, or network needed
        assertTrue(SdkPriming.prime(true));
        assertTrue(SdkPriming.prime(true), "second call is a no-op");
    }
}
