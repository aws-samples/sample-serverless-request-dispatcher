// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.benchmark;

import com.amazonaws.serverless.requestdispatcher.annotation.GetMapping;

import javax.inject.Inject;

/**
 * Serves the benchmark route without touching any AWS service, so initialization
 * contains only the JVM, Dagger, and the dispatcher's route scan.
 */
public class PingController {

    @Inject
    public PingController() {
    }

    @GetMapping("/products")
    public String listProducts() {
        return "[]";
    }
}
