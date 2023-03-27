/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control;

import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl;
import com.aws.greengrass.testing.mqtt.client.control.api.EngineControl;
import com.aws.greengrass.testing.mqtt.client.control.api.EngineControl.EngineEvents;
import com.aws.greengrass.testing.mqtt.client.control.implementation.EngineControlImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main class of standalone application.
 */
public class ExampleControl {
    private static final int DEFAULT_PORT = 47_619;
    private static final boolean DEFAULT_USE_TLS = true;
    private static final long EXECUTOR_SHUTDOWN_MS = 10_000;

    private static final Logger logger = LogManager.getLogger(ExampleControl.class);
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private final boolean useTLS;
    private final int port;
    private final EngineControl engine;

    private final EngineEvents engineEvents = new EngineEvents() {
        @Override
        public void onAgentAttached(AgentControl agent) {
            logger.atInfo().log("Agent {} is connected", agent.getAgentId());
            AgentTestScenario scenario = new AgentTestScenario(useTLS, engine, agent);
            executorService.submit(scenario);
        }

        @Override
        public void onAgentDeattached(AgentControl agent) {
            logger.atInfo().log("Agent {} is disconnected", agent.getAgentId());
            // TODO: find agent object by Id, terminate it and join thread
        }
    };


    /**
     * Creates instance of ExampleControl.
     * @param useTLS should use TLS when files with credentials available
     * @param port port of gRPC server to listen
     */
    public ExampleControl(boolean useTLS, int port) {
        super();
        this.useTLS = useTLS;
        this.port = port;
        this.engine = new EngineControlImpl();
    }

    private void testRun() throws IOException, InterruptedException {
        engine.startEngine(port, engineEvents);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                logger.atInfo().log("*** shutting down gRPC server since JVM is shutting down");
                engine.stopEngine();
                shutdownExecutorService();
                logger.atInfo().log("*** server shut down");
            }
        });

        engine.awaitTermination();
    }

    private void shutdownExecutorService() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(EXECUTOR_SHUTDOWN_MS, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Main launches the server from the command line.
     * @param args program agruments
     * @throws IOException on IO errors
     * @throws InterruptedException when thread is interrupted
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        // The port on which the server should run
        int port = DEFAULT_PORT;
        boolean useTLS = DEFAULT_USE_TLS;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);

            if (args.length > 1) {
                useTLS = Boolean.valueOf(args[1]);
            }
        }
        ExampleControl test = new ExampleControl(useTLS, port);
        test.testRun();
    }
}
