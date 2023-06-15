/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation;

import com.aws.greengrass.testing.mqtt.client.Mqtt5ConnAck;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Properties;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Subscription;
import com.aws.greengrass.testing.mqtt.client.MqttCloseRequest;
import com.aws.greengrass.testing.mqtt.client.MqttConnectReply;
import com.aws.greengrass.testing.mqtt.client.MqttConnectionId;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt.client.MqttPublishRequest;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.MqttUnsubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import lombok.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Implementation of ConnectionControl.
 */
public class ConnectionControlImpl implements ConnectionControl {

    private final int connectionId;
    private final Mqtt5ConnAck connAck;
    private final AgentControl.ConnectionEvents connectionEvents;
    private final AgentControlImpl agentControl;
    private int timeout;
    private String connectionName;

    /**
     * Creates instanse of ConnectionControlImpl.
     *
     * @param connectReply response to connect request from agent
     * @param connectionEvents received of connection events
     * @param agentControl backreference to agentControl
     */
    ConnectionControlImpl(MqttConnectReply connectReply, @NonNull AgentControl.ConnectionEvents connectionEvents,
                            @NonNull AgentControlImpl agentControl) {
        super();
        this.connectionId = connectReply.getConnectionId().getConnectionId();
        this.connAck = connectReply.getConnAck();
        this.connectionEvents = connectionEvents;
        this.agentControl = agentControl;
        this.timeout = agentControl.getTimeout();
        this.connectionName = buildConnectionName(agentControl.getAgentId(), this.connectionId);
    }

    @Override
    public AgentControlImpl getAgentControl() {
        return agentControl;
    }

    @Override
    public int getConnectionId() {
        return connectionId;
    }

    @Override
    public void setConnectionName(@NonNull String connectionName) {
        this.connectionName = connectionName;
    }

    @Override
    public String getConnectionName() {
        return connectionName;
    }

    @Override
    public Mqtt5ConnAck getConnAck() {
        return connAck;
    }

    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public void closeMqttConnection(int reason, List<Mqtt5Properties> userProperties) {
        MqttCloseRequest.Builder builder = MqttCloseRequest.newBuilder()
                    .setConnectionId(MqttConnectionId.newBuilder().setConnectionId(connectionId).build())
                    .setTimeout(timeout)
                    .setReason(reason);

        if (userProperties != null) {
            builder.addAllProperties(userProperties);
        }

        agentControl.closeMqttConnection(builder.build());
    }

    @Override
    public MqttSubscribeReply subscribeMqtt(Integer subscriptionId, List<Mqtt5Properties> userProperties,
                                            @NonNull Mqtt5Subscription... subscriptions) {
        MqttSubscribeRequest.Builder builder = MqttSubscribeRequest.newBuilder()
                    .setConnectionId(MqttConnectionId.newBuilder().setConnectionId(connectionId).build())
                    .setTimeout(timeout)
                    .addAllSubscriptions(Arrays.asList(subscriptions));

        if (subscriptionId != null) {
            builder.setSubscriptionId(subscriptionId);
        }

        if (userProperties != null) {
            builder.addAllProperties(userProperties);
        }

        return agentControl.subscribeMqtt(builder.build());
    }

    @Override
    public MqttSubscribeReply unsubscribeMqtt(List<Mqtt5Properties> userProperties, @NonNull String... filters) {
        MqttUnsubscribeRequest.Builder builder = MqttUnsubscribeRequest.newBuilder()
                    .setConnectionId(MqttConnectionId.newBuilder().setConnectionId(connectionId).build())
                    .setTimeout(timeout)
                    .addAllFilters(Arrays.asList(filters));

        if (userProperties != null) {
            builder.addAllProperties(userProperties);
        }

        return agentControl.unsubscribeMqtt(builder.build());
    }

    @Override
    public MqttPublishReply publishMqtt(@NonNull Mqtt5Message message) {
        MqttPublishRequest publishRequest = MqttPublishRequest.newBuilder()
                .setConnectionId(MqttConnectionId.newBuilder().setConnectionId(connectionId).build())
                .setTimeout(timeout)
                .setMsg(message)
                .build();
        return agentControl.publishMqtt(publishRequest);
    }

    /**
     * Called when MQTT message has been received.
     *
     * @param message the received MQTT message
     */
    void onMessageReceived(@NonNull Mqtt5Message message) {
        if (connectionEvents != null) {
            connectionEvents.onMessageReceived(this, message);
        }
    }

    /**
     * Called when MQTT connection has been disconnected.
     *
     * @param disconnect optional infomation from DISCONNECT packet
     * @param error optional OS-dependent error string
     */
    void onMqttDisconnect(Mqtt5Disconnect disconnect, String error) {
        if (connectionEvents != null) {
            connectionEvents.onMqttDisconnect(this, disconnect, error);
        }
    }

    static String buildConnectionName(String agentId, int connectionId) {
        return "agent:" + agentId + ";connection:" + connectionId;
    }
}
