// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.serverless.sample.benchmark;

import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.List;

@Module
public class MinimalModule {

    @Provides
    @Singleton
    public List<Object> provideControllers(PingController pingController) {
        return List.of(pingController);
    }
}
