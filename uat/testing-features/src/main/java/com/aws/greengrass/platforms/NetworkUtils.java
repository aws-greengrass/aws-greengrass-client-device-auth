/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.platforms;

import java.io.IOException;

public abstract class NetworkUtils {
    protected static final String[] MQTT_PORTS = {"8883", "443"};

    public abstract void disconnectMqtt() throws InterruptedException, IOException;

    public abstract void recoverMqtt() throws InterruptedException, IOException;
}
