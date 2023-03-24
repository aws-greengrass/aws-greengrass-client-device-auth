/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation.grpc;

import com.aws.greengrass.testing.mqtt.client.RegisterReply;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Interceptor to get IP address of the client.
 */
public class GRPCDiscoveryServerInterceptor implements ServerInterceptor {
    private static final Logger logger = LogManager.getLogger(GRPCDiscoveryServerInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        final SocketAddress address = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        if (address instanceof InetSocketAddress) {
            final InetSocketAddress inetAddress = (InetSocketAddress)address;
            final String ip = inetAddress.getHostString();
            // logger.atInfo().log("interceptCall ip: {}", ip);

            SimpleForwardingServerCall<ReqT, RespT> responseInterceptingServerCall
                        = new SimpleForwardingServerCall<ReqT, RespT>(call) {
                @SuppressWarnings("unchecked")
                @Override
                public void sendMessage(RespT message) {
                    if (message instanceof RegisterReply) {
                        RegisterReply reply = RegisterReply.newBuilder().setAddress(ip).build();
                        message = (RespT)reply;
                    }
                    super.sendMessage(message);
                }
            };

            return next.startCall(responseInterceptingServerCall, headers);
        } else {
            logger.atWarn().log("Could not get client's address");
            return next.startCall(call, headers);
        }
    }
}
