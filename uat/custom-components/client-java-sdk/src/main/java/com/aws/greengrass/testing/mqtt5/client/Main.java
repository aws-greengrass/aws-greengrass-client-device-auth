/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.ClientException;
import com.aws.greengrass.testing.mqtt5.client.grpc.GRPCLibImpl;
import com.aws.greengrass.testing.mqtt5.client.sdkmqtt.MqttLibImpl;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.UtilityClass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Main class of application.
 */
@UtilityClass
public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final String DEFAULT_GRPC_SERVER_IP = "127.0.0.1";
    private static final int DEFAULT_GRPC_SERVER_PORT = 47_619;

    private static final int PORT_MIN = 1;
    private static final int PORT_MAX = 65_535;

    @Data
    @AllArgsConstructor
    private static class Arguments {
        String agentId;
        String[] hosts;
        int port;
    }

    /**
     * The main method of application.
     *
     * @param args program's arguments agentId [[IP ] port]
     */
    @SuppressWarnings({"PMD.DoNotCallSystemExit", "PMD.AvoidCatchingGenericException"})
    public static void main(String[] args) {
        int rc;
        try {
            doAll(args);
            logger.atInfo().log("Execution done successfully");
            rc = 0;
        } catch (IllegalArgumentException ex) {
            logger.atError().withThrowable(ex).log("Invalid arguments");
            printUsage();
            rc = 1;
        } catch (ClientException ex) {
            logger.atError().withThrowable(ex).log("ClientException");
            rc = 2;
        } catch (Exception ex) {
            logger.atError().withThrowable(ex).log("Exception");
            rc = 3;
        }
        System.exit(rc);
    }

    @SuppressWarnings({"PMD.MissingBreakInSwitch",
                        "PMD.ImplicitSwitchFallThrough",
                        "PMD.DefaultLabelNotLastInSwitchStmt"})
    private static Arguments parseArgs(String... args) {
        String agentId;
        List<String> addresses = new ArrayList<>();
        int port = DEFAULT_GRPC_SERVER_PORT;

        switch (args.length) {
            default:
            case 3:
                // agent_id port ip ...
                for (int i = 2; i < args.length; i++) {
                    addresses.add(args[i]);
                }
                // fallthrough
            case 2:
                // agent_id port
                port = Integer.parseInt(args[1]);
                if (port < PORT_MIN || port > PORT_MAX) {
                    throw new IllegalArgumentException("Invalid port value " + port + " , expected [1..65535]");
                }
                // fallthrough
            case 1:
                // agent_id
                addresses.add(DEFAULT_GRPC_SERVER_IP);
                agentId = args[0];
                break;
            case 0:
                throw new IllegalArgumentException("Missing argument(s)");
        }
        return new Arguments(agentId, addresses.toArray(new String[0]), port);
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private static void doAll(String... args) throws Exception {
        Arguments arguments = parseArgs(args);

        GRPCLib gprcLib = new GRPCLibImpl();
        GRPCLink link = gprcLib.makeLink(arguments.getAgentId(), arguments.getHosts(), arguments.getPort());

        try (MqttLib mqttLib = new MqttLibImpl()) {
            String reason = link.handleRequests(mqttLib);
            link.shutdown(reason);
        }
    }

    private static void printUsage() {
        logger.atWarn().log("Usage: agent_id [[port] ip ...]\n"
                            + "         agent_id\tidentification string for that agent for the control\n"
                            + "         port\tTCP port of gRPC service of the control\n"
                            + "         ip\tIP address of gRPC service of the control\n"
        );
    }
}
