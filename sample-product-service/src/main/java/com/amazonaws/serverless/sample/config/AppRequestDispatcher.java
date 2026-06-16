// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import com.amazonaws.serverless.requestdispatcher.annotation.FrontControllerRequestDispatcher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class AppRequestDispatcher extends FrontControllerRequestDispatcher {

    @Inject
    public AppRequestDispatcher(List<Object> controllers) {
        super(controllers, "/api");
    }
}
