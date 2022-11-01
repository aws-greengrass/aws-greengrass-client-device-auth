/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.infra;

import java.util.function.Consumer;

public interface NetworkStateProvider {
    enum ConnectionState {
        NETWORK_UP,
        NETWORK_DOWN,
    }

    void registerHandler(Consumer<ConnectionState> networkChangeHandler);

    ConnectionState getConnectionState();
}
