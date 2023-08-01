/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation.addon;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventFilter;
import lombok.Getter;
import lombok.NonNull;

/**
 * Implements event about MQTT connection has been disconnected.
 */
public class MqttDisconnectEvent extends EventImpl {

    private final ConnectionControl connectionControl;

    @Getter
    private final Mqtt5Disconnect disconnect;

    @Getter
    private final String osError;

    /**
     * Creates instance of MqttDisconnectEvent.
     *
     * @param connectionControl the connection control which receives that message
     * @param disconnect the disconnect gRPC event
     * @param osError the OS error as received from the client
     */
    public MqttDisconnectEvent(@NonNull ConnectionControl connectionControl, @NonNull Mqtt5Disconnect disconnect,
                                String osError) {
        super(Type.EVENT_TYPE_MQTT_DISCONNECTED);
        this.connectionControl = connectionControl;
        this.disconnect = disconnect;
        this.osError = osError;
    }

    @Override
    public String getConnectionName() {
        return connectionControl.getConnectionName();
    }

    @Override
    public boolean isMatched(@NonNull EventFilter filter) {
        // check type and timestamp
        boolean matched = super.isMatched(filter);
        if (!matched) {
            return false;
        }

        // check connection
        matched = compareConnection(filter.getConnectionControl(), filter.getAgentId(), filter.getConnectionId(), 
                                    filter.getConnectionName());
        return matched;
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private boolean compareConnection(ConnectionControl expectedConnectionControl, String agentId, Integer connectionId,
                                        String connectionName) {
        // 1'st priority
        if (expectedConnectionControl != null) {
            // compare references !
            return expectedConnectionControl == connectionControl;
        }

        // 2'nd priority
        if (agentId != null && connectionId != null) {
            return agentId.equals(connectionControl.getAgentControl().getAgentId())
                        && connectionId == connectionControl.getConnectionId();
        }

        // 3'th priority
        // check connection name
        return connectionName == null || connectionName.equals(getConnectionName());
    }
}
