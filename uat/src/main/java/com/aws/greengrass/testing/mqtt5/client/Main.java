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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class of application.
 */
@UtilityClass
public class Main {

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final String DEFAULT_GRPC_SERVER_IP = "127.0.0.1";
    private static final int DEFAULT_GRPC_SERVER_PORT = 47_619;

    private static final int PORT_MIN = 1;
    private static final int PORT_MAX = 65_535;

    private static final Logger logger = Logger.getLogger(Main.class.getName());


    @Data
    @AllArgsConstructor
    private static class Arguments {
        String agentId;
        String host;
        int port;
    }


    /**
     * The main method of application.
     *
     * @param args program's arguments
     */
    @SuppressWarnings({"PMD.DoNotCallSystemExit", "PMD.AvoidCatchingGenericException"})
    public static void main(String[] args) {
        int rc;
        try {
            doAll(args);
            logger.log(Level.INFO, "Execution done");
            rc = 0;
        } catch (IllegalArgumentException ex) {
            logger.log(Level.WARNING, "Invalid arguments", ex);
            printUsage();
            rc = 1;
        } catch (ClientException ex) {
            logger.log(Level.WARNING, "ClientException", ex);
            rc = 2;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Exception", ex);
            rc = 3;
        }
        System.exit(rc);
    }


    @SuppressWarnings("PMD.MissingBreakInSwitch")
    private static Arguments parseArgs(String... args) {
        String agentId;
        String address = DEFAULT_GRPC_SERVER_IP;
        int port = DEFAULT_GRPC_SERVER_PORT;

        switch (args.length) {
            case 3:
                // agent_id ip port
                port = Integer.parseInt(args[2]);
                if (port < PORT_MIN || port > PORT_MAX) {
                    throw new IllegalArgumentException("Invalid port value " + port + " , expected [1..65535]");
                }
                // fallthrough
            case 2:
                // agent_id ip
                address = args[1];
                // fallthrough
            case 1:
                // agent_id
                agentId = args[0];
                break;
            default:
                throw new IllegalArgumentException("Invalid number of arguments, expected [1..3] but got "
                                                        + args.length);
        }
        return new Arguments(agentId, address, port);
    }

    private static void doAll(String... args) throws Exception {
        Arguments arguments = parseArgs(args);

        GRPCLib gprcLib = new GRPCLibImpl();
        GRPCLink link = gprcLib.makeLink(arguments.getAgentId(), arguments.getHost(), arguments.getPort());

        try (MqttLib mqttLib = new MqttLibImpl()) {
            String reason = link.handleRequests(mqttLib);
            link.shutdown(reason);
        }
    }

    private static void printUsage() {
        logger.log(Level.INFO, "Usage: agent_id [host [port]]");
    }
}
