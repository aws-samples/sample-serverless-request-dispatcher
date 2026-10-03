// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.benchmark;

import com.amazonaws.serverless.requestdispatcher.annotation.FrontControllerRequestDispatcher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class MinimalRequestDispatcher extends FrontControllerRequestDispatcher {

    @Inject
    public MinimalRequestDispatcher(List<Object> controllers) {
        super(controllers, "/api");
    }
}
