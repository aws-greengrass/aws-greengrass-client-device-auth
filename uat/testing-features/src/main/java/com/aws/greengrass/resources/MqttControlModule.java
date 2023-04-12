/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.resources;

import com.aws.greengrass.testing.mqtt.client.control.api.EngineControl;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventStorage;
import com.aws.greengrass.testing.mqtt.client.control.implementation.EngineControlImpl;
import com.aws.greengrass.testing.mqtt.client.control.implementation.addon.EventStorageImpl;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

import javax.inject.Singleton;

@AutoService(Module.class)
public class MqttControlModule extends AbstractModule {

    @Provides
    @Singleton
    EngineControl providesEngineControlImpl() {
        return new EngineControlImpl();
    }

    @Provides
    @Singleton
    EventStorage providesEventStorageImpl() {
        return new EventStorageImpl();
    }
}
