// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.requestdispatcher.annotation;

public class RouteException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RouteException(String message) {
		super(message);
	}

}
