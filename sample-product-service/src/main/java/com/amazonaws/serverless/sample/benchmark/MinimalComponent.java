// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.benchmark;

import dagger.Component;

import javax.inject.Singleton;

@Component(modules = {MinimalModule.class})
@Singleton
public interface MinimalComponent {
    MinimalRequestDispatcher requestDispatcher();
}
