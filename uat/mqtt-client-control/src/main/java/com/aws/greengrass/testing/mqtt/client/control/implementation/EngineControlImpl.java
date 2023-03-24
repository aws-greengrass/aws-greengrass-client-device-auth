/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.control.api.EngineControl;
import com.aws.greengrass.testing.mqtt.client.control.implementation.grpc.GRPCDiscoveryServer;
import com.aws.greengrass.testing.mqtt.client.control.implementation.grpc.GRPCDiscoveryServerInterceptor;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class EngineControlImpl implements EngineControl, DiscoveryEvents {
    private static final Logger logger = LogManager.getLogger(EngineControlImpl.class);

    private final ConcurrentHashMap<String, AgentControlImpl> agents = new ConcurrentHashMap<>();
    private final AtomicReference<Server> server = new AtomicReference<>();


    private EngineEvents engineEvents;

    @Override
    public void startEngine(int port, @NonNull EngineEvents engineEvents) throws IOException {
        this.engineEvents = engineEvents;
        ArrayList<ServerInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new GRPCDiscoveryServerInterceptor());

        Server srv = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(ServerInterceptors.intercept(new GRPCDiscoveryServer(this), interceptors))
                .build()
                .start();

        Server oldSrv = server.getAndSet(srv);
        if (oldSrv != null) {
            oldSrv.shutdown();
        }
        logger.atInfo().log("gRPC MQTT client control server started, listening on {}", port);
    }

    @Override
    public boolean isEngineRunning() {
        return server.get() != null;
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        Server srv = server.get();
        if (srv != null) {
            srv.awaitTermination();
        }
    }

    @Override
    public void stopEngine() {
        Server srv = server.getAndSet(null);
        if (srv != null) {
            unregisterAllAgent();
            srv.shutdown();
            logger.atInfo().log("gRPC MQTT client control server stopped");
        }
    }


    @Override
    public void onDiscoveryAgent(String agentId, String address, int port) {
        final boolean[] isNew = new boolean[1];
        isNew[0] = false;
        AgentControlImpl agent = agents.computeIfAbsent(agentId, k -> {
            isNew[0] = true;
            return new AgentControlImpl(agentId, address, port);
            });

        if (isNew[0]) {
            logger.atInfo().log("Created new agent control for {} on {}:{}", agentId, address, port);
            agent.startAgent();
            if (engineEvents != null) {
                engineEvents.onAgentAttached(agent);
            }
        }
    }


    @Override
    public void onUnregisterAgent(String agentId) {
        AgentControlImpl agent = agents.remove(agentId);
        if (agent != null) {
            agent.stopAgent();
            if (engineEvents != null) {
                engineEvents.onAgentDeattached(agent);
            }
        }
    }


    @Override
    public void onMessageReceived(String agentId, int connectionId, Mqtt5Message message) {
        AgentControlImpl agentControl = agents.get(agentId);
        if (agentControl == null) {
            logger.atWarn().log("Message received but agent {} could not found", agentId);
        } else {
            agentControl.onMessageReceived(connectionId, message);
        }
    }


    @Override
    public void onMqttDisconnect(String agentId, int connectionId, Mqtt5Disconnect disconnect, String error) {
        AgentControlImpl agentControl = agents.get(agentId);
        if (agentControl == null) {
            logger.atWarn().log("MQTT disconnect received but agent {} could not found", agentId);
        } else {
            agentControl.onMqttDisconnect(connectionId, disconnect, error);
        }
    }


    private void unregisterAllAgent() {
        agents.forEach((agentId, agent) -> {
            if (agents.remove(agentId, agent)) {
                agent.stopAgent();
                if (engineEvents != null) {
                    engineEvents.onAgentDeattached(agent);
                }
            }
        });
    }
}
