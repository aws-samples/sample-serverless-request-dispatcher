// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.requestdispatcher.annotation;

import java.util.List;

/**
 * Concrete subclass of FrontControllerRequestDispatcher for testing.
 */
public class TestRequestDispatcher extends FrontControllerRequestDispatcher {

    public TestRequestDispatcher(List<Object> controllers) {
        super(controllers, "");
    }
}
