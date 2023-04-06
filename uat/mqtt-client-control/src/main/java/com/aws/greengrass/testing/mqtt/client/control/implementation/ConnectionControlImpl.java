/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation;

import com.aws.greengrass.testing.mqtt.client.Mqtt5ConnAck;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Subscription;
import com.aws.greengrass.testing.mqtt.client.MqttCloseRequest;
import com.aws.greengrass.testing.mqtt.client.MqttConnectReply;
import com.aws.greengrass.testing.mqtt.client.MqttConnectionId;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt.client.MqttPublishRequest;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.MqttUnsubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl.ConnectionEvents;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import lombok.NonNull;

import java.util.Arrays;

/**
 * Implementation of ConnectionControl.
 */
public class ConnectionControlImpl implements ConnectionControl {

    private final int connectionId;
    private final Mqtt5ConnAck connAck;
    private final ConnectionEvents connectionEvents;
    private final AgentControlImpl agent;
    private int timeout;
    private String connectionName;

    /**
     * Creates instanse of ConnectionControlImpl.
     *
     * @param connectReply response to connect request from agent
     * @param connectionEvents received of connection events
     * @param agent backreference to agent
     */
    ConnectionControlImpl(MqttConnectReply connectReply, @NonNull ConnectionEvents connectionEvents,
                            @NonNull AgentControlImpl agent) {
        super();
        this.connectionId = connectReply.getConnectionId().getConnectionId();
        this.connAck = connectReply.getConnAck();
        this.connectionEvents = connectionEvents;
        this.agent = agent;
        this.timeout = agent.getTimeout();
        this.connectionName = buildConnectionName(agent.getAgentId(), this.connectionId);
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
    public void closeMqttConnection(int reason) {
        MqttCloseRequest closeRequest = MqttCloseRequest.newBuilder()
                    .setConnectionId(MqttConnectionId.newBuilder().setConnectionId(connectionId).build())
                    .setTimeout(timeout)
                    .setReason(reason)
                    .build();
        agent.closeMqttConnection(closeRequest);
    }

    @Override
    public MqttSubscribeReply subscribeMqtt(Integer subscriptionId, @NonNull Mqtt5Subscription... subscriptions) {
        MqttSubscribeRequest.Builder builder = MqttSubscribeRequest.newBuilder()
                .setConnectionId(MqttConnectionId.newBuilder().setConnectionId(connectionId).build())
                .setTimeout(timeout)
                .addAllSubscriptions(Arrays.asList(subscriptions));

        if (subscriptionId != null) {
            builder.setSubscriptionId(subscriptionId);
        }

        return agent.subscribeMqtt(builder.build());
    }

    @Override
    public MqttSubscribeReply unsubscribeMqtt(@NonNull String... filters) {
        MqttUnsubscribeRequest unsubscribeRequest =  MqttUnsubscribeRequest.newBuilder()
                    .setConnectionId(MqttConnectionId.newBuilder().setConnectionId(connectionId).build())
                    .setTimeout(timeout)
                    .addAllFilters(Arrays.asList(filters))
                    .build();

        return agent.unsubscribeMqtt(unsubscribeRequest);
    }

    @Override
    public MqttPublishReply publishMqtt(@NonNull Mqtt5Message message) {
        MqttPublishRequest publishRequest = MqttPublishRequest.newBuilder()
            .setConnectionId(MqttConnectionId.newBuilder().setConnectionId(connectionId).build())
            .setTimeout(timeout)
            .setMsg(message)
            .build();
        return agent.publishMqtt(publishRequest);
    }

    /**
     * Called when MQTT message has been received.
     *
     * @param message the received MQTT message
     */
    void onMessageReceived(Mqtt5Message message) {
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
