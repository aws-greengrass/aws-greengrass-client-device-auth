/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.ClientException;
import com.aws.greengrass.testing.mqtt5.client.grpc.GRPCLibImpl;
import com.aws.greengrass.testing.mqtt5.client.grpc.GRPCLinkImpl;
import com.aws.greengrass.testing.mqtt5.client.sdkmqtt.MqttConnectionImpl;
import com.aws.greengrass.testing.mqtt5.client.sdkmqtt.MqttLibImpl;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class of application.
 */
//@SuppressWarnings("PMD.UseUtilityClass")
public class Main {

    private static final String DEFAULT_GRPC_SERVER_IP = "127.0.0.1";
    private static final int DEFAULT_GRPC_SERVER_PORT = 47619;

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
    public static void main(String[] args) {
        try {
            doAll(args);
            logger.log(Level.INFO, "Execution done");
            System.exit(0);
        } catch (IllegalArgumentException ex) {
            logger.log(Level.WARNING, "Invalid arguments", ex);
            printUsage();
            System.exit(1);
        } catch (ClientException ex) {
            logger.log(Level.WARNING, "ClientException", ex);
            System.exit(1);
        }
    }


    private static Arguments parseArgs(String[] args) throws IllegalArgumentException {
        String agentId;
        String address = DEFAULT_GRPC_SERVER_IP;
        int port = DEFAULT_GRPC_SERVER_PORT;

        switch (args.length) {
            case 3:
                // agent_id ip port
                port = Integer.parseInt(args[2]);
                if (port < PORT_MIN || port > PORT_MAX) {
                    throw new IllegalArgumentException("Invalid port value %s, expected [1..65535]");
                }
                // fallthru
            case 2:
                // agent_id ip
                address = args[1];
                // fallthru
            case 1:
                // agent_id
                agentId = args[0];
                break;
            default:
                throw new IllegalArgumentException("Invalid number of arguments, expected [1..3]");
        }
        return new Arguments(agentId, address, port);
    }

    private static void doAll(String[] args) throws IllegalArgumentException, ClientException {
        Arguments arguments = parseArgs(args);

        GRPCLib gprcLib = new GRPCLibImpl();
        GRPCLink link = gprcLib.makeLink(arguments.getAgentId(), arguments.getHost(), arguments.getPort());

        MqttLib mqttLib = new MqttLibImpl();
        String reason = link.handleRequests(mqttLib);
        link.shutdown(reason);
    }

    private static void printUsage() {
        logger.log(Level.INFO, "Usage: agent_id [host [port]]");
    }
}
