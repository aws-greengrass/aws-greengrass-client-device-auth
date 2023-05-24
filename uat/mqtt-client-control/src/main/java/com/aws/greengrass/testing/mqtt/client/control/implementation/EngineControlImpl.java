/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation;


import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;


public class EngineControlImpl implements EngineControl, DiscoveryEvents {
    private static final Logger logger = LogManager.getLogger(EngineControlImpl.class);

    private final AgentControlFactory agentControlFactory;
    private final ConcurrentHashMap<String, AgentControlImpl> agents = new ConcurrentHashMap<>();
    private final AtomicReference<Server> server = new AtomicReference<>();

    private EngineEvents engineEvents;
    private Integer boundPort = null;
    private int timeout = DEFAULT_TIMEOUT;


    interface AgentControlFactory {
        AgentControlImpl newAgentControl(@NonNull EngineControlImpl engineControl, @NonNull String agentId,
                                            @NonNull String address, int port);
    }

    /**
     * Creates instance of EngineControlImpl.
     */
    public EngineControlImpl() {
        this(new AgentControlFactory() {
            @Override
            public AgentControlImpl newAgentControl(@NonNull EngineControlImpl engineControl, @NonNull String agentId, 
                                                    @NonNull String address, int port) {
                return new AgentControlImpl(engineControl, agentId, address, port);
            }
        });
    }

    /**
     * Creates instance of EngineControlImpl for tests.
     *
     * @param agentControlFactory the factory to create agent control
     */
    EngineControlImpl(@NonNull AgentControlFactory agentControlFactory) {
        super();
        this.agentControlFactory = agentControlFactory;
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
    public void startEngine(int port, @NonNull EngineEvents engineEvents) throws IOException {
        this.engineEvents = engineEvents;
        ArrayList<ServerInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new GRPCDiscoveryServerInterceptor());

        Server srv = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(ServerInterceptors.intercept(new GRPCDiscoveryServer(this), interceptors))
                .build()
                .start();
        boundPort = srv.getPort();

        Server oldSrv = server.getAndSet(srv);
        if (oldSrv != null) {
            oldSrv.shutdown();
        }
        logger.atInfo().log("MQTT client control gRPC server started, listening on {}", boundPort);
    }

    @Override
    public Integer getBoundPort() {
        return boundPort;
    }

    @Override
    public String[] getIPs() {
        return getLocalIPList().toArray(new String[0]);
    }

    @Override
    public boolean isEngineRunning() {
        return server.get() != null;
    }

    @Override
    public AgentControl getAgent(@NonNull String agentId) {
        return agents.get(agentId);
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        Server srv = server.get();
        if (srv != null) {
            srv.awaitTermination();
        }
    }

    @Override
    public ConnectionControl getConnectionControl(@NonNull String connectionName) {
        ConnectionControl connectionControl = null;
        for (ConcurrentHashMap.Entry<String, AgentControlImpl> entry : agents.entrySet()) {
            connectionControl = entry.getValue().getConnectionControl(connectionName);
            if (connectionControl != null) {
                break;
            }
        }
        return connectionControl;
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
    public void onDiscoveryAgent(@NonNull String agentId, @NonNull String address, int port) {
        final boolean[] isNew = new boolean[1];
        isNew[0] = false;
        AgentControlImpl agentControl = agents.computeIfAbsent(agentId, k -> {
            isNew[0] = true;
            return agentControlFactory.newAgentControl(this, agentId, address, port);
            });

        if (isNew[0]) {
            // completely new agent
            logger.atInfo().log("Created new agent control for {} on {}:{}", agentId, address, port);
            agentControl.startAgent();
            if (engineEvents != null) {
                engineEvents.onAgentAttached(agentControl);
            }
        } else {
            // check agent is relocated after restart
            if (agentControl.isOnThatAddress(address, port)) {
                logger.atInfo().log("Agent {} on {}:{} send duplicated DiscoveryAgent", agentId, address, port);
            } else {
                logger.atInfo().log("Agent {} relocated to {}:{}", agentId, address, port);
                agents.remove(agentId);
                // recursion
                onDiscoveryAgent(agentId, address, port);
            }
        }
    }

    @Override
    public void onUnregisterAgent(@NonNull String agentId) {
        AgentControlImpl agentControl = agents.remove(agentId);
        if (agentControl != null) {
            agentControl.stopAgent();
            if (engineEvents != null) {
                engineEvents.onAgentDeattached(agentControl);
            }
        }
    }

    @Override
    public void onMessageReceived(@NonNull String agentId, int connectionId, @NonNull Mqtt5Message message) {
        AgentControlImpl agentControl = agents.get(agentId);
        if (agentControl == null) {
            logger.atWarn().log("Message received but agent id {} could not found", agentId);
        } else {
            agentControl.onMessageReceived(connectionId, message);
        }
    }

    @Override
    public void onMqttDisconnect(String agentId, int connectionId, Mqtt5Disconnect disconnect, String error) {
        AgentControlImpl agentControl = agents.get(agentId);
        if (agentControl == null) {
            logger.atWarn().log("MQTT disconnect received but agent id {} could not found", agentId);
        } else {
            agentControl.onMqttDisconnect(connectionId, disconnect, error);
        }
    }


    private void unregisterAllAgent() {
        agents.forEach((agentId, agentControl) -> {
            if (agents.remove(agentId, agentControl)) {
                if (engineEvents != null) {
                    engineEvents.onAgentDeattached(agentControl);
                }
            }
        });
    }

    private List<String> getLocalIPList() {
        List<String> ips = new ArrayList<>();

        try {
            for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements();) {
                NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
                for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements();) {
                    InetAddress inetAddress = (InetAddress) inetAddrs.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ips.add(inetAddress.getHostAddress());
                    }
                }
            }
        } catch (SocketException ex) {
            logger.atError().withThrowable(ex).log("Couldn't get local IP addresses");
        }
        return ips;
    }
}
