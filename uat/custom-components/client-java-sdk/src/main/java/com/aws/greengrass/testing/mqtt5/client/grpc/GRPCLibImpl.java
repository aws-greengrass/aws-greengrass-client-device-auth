/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt5.client.GRPCLib;
import com.aws.greengrass.testing.mqtt5.client.GRPCLink;
import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;
import lombok.NonNull;

import java.util.List;

/**
 * Implementation of gRPC library.
 */
public class GRPCLibImpl implements GRPCLib {
    private final LinkFactory linkFactory;

    interface LinkFactory {
        GRPCLink newLink(@NonNull String agentId, @NonNull List<String> hosts, int port) throws GRPCException;
    }

    /**
     * Creates instance of GRPCLibImpl.
     */
    public GRPCLibImpl() {
        this(new LinkFactory() {
            @Override
            public GRPCLink newLink(@NonNull String agentId, @NonNull List<String> hosts, int port)
                    throws GRPCException {
                return new GRPCLinkImpl(agentId, hosts, port);
            }
        });
    }

    /**
     * Creates instance of GRPCLibImpl for tests.
     *
     * @param linkFactory the factory for gRPC links
     */
    GRPCLibImpl(@NonNull LinkFactory linkFactory) {
        super();
        this.linkFactory = linkFactory;
    }

    @Override
    public GRPCLink makeLink(@NonNull String agentId, @NonNull List<String> hosts, int port) throws GRPCException {
        return linkFactory.newLink(agentId, hosts, port);
    }
}
