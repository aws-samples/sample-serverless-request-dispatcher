// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

/**
 * Comparison handlers used only by the cold start benchmark in {@code benchmarks/}.
 *
 * <ul>
 *   <li>{@link com.amazonaws.serverless.sample.benchmark.MinimalHandler} — dispatcher and Dagger with a
 *       controller that creates no AWS SDK clients. Isolates the cost of the routing layer.</li>
 *   <li>{@link com.amazonaws.serverless.sample.benchmark.PlainHandler} — the same product controller and
 *       SDK clients wired by hand and dispatched with a switch statement. The difference between this
 *       handler and {@link com.amazonaws.serverless.sample.LambdaHandler} is the cost of the dispatcher
 *       plus Dagger.</li>
 * </ul>
 *
 * The production sample does not use these classes.
 */
package com.amazonaws.serverless.sample.benchmark;
