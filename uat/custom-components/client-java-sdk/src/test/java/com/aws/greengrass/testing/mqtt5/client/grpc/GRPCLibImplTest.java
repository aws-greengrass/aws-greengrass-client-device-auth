/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt5.client.GRPCLink;
import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class GRPCLibImplTest {

    private GRPCLibImpl.LinkFactory linkFactory;
    private GRPCLibImpl gRPCLibImpl;

    @BeforeEach
    void setup() {
        linkFactory = mock(GRPCLibImpl.LinkFactory.class);
        gRPCLibImpl = new GRPCLibImpl(linkFactory);
    }

    @Test
    void GIVEN_link_WHEN_makeLink_THEN_that_link_returned() throws GRPCException {
        // GIVEN
        final String agentId = "agentId";
        final String[] hosts = {"hostname"};
        final int port = 9999;

        GRPCLink gRPCLink = mock(GRPCLink.class);
        when(linkFactory.newLink(eq(agentId), eq(hosts), eq(port))).thenReturn(gRPCLink);

        // WHEN
        GRPCLink actualGRPCLink = gRPCLibImpl.makeLink(agentId, hosts, port);

        // THEN
        assertSame(gRPCLink, actualGRPCLink);
    }
}
