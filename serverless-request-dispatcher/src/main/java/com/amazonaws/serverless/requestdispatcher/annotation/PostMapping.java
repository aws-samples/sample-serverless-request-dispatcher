// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.requestdispatcher.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import lombok.NonNull;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostMapping {

	@NonNull
	public String value();
	
}
