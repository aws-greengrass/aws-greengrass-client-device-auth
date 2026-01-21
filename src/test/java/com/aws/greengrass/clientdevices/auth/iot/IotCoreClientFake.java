/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IotCoreClientFake implements IotCoreClient {

    private final Map<String, String> attributes = new ConcurrentHashMap<>();
    private volatile boolean down;

    public void setThingAttributes(Map<String, String> attributes) {
        this.attributes.clear();
        this.attributes.putAll(attributes);
    }

    public void down() {
        down = true;
    }

    public void up() {
        down = false;
    }

    @Override
    public Map<String, String> getThingAttributes(String thingName) throws CloudServiceInteractionException {
        if (down) {
            throw new CloudServiceInteractionException("service is down");
        }
        return new HashMap<>(attributes);
    }
}
