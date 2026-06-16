// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.config;

import dagger.Component;

import javax.inject.Singleton;

@Component(modules = {AppModule.class})
@Singleton
public interface AppComponent {
    AppRequestDispatcher requestDispatcher();
}
