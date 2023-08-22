/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.discover;

import com.aws.greengrass.testing.mqtt.client.CoreDeviceConnectivityInfo;
import com.aws.greengrass.testing.mqtt.client.CoreDeviceDiscoveryReply;
import com.aws.greengrass.testing.mqtt.client.CoreDeviceDiscoveryRequest;
import com.aws.greengrass.testing.mqtt.client.CoreDeviceGroup;
import com.aws.greengrass.testing.mqtt5.client.exceptions.DiscoveryException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContextOptions;
import software.amazon.awssdk.iot.discovery.DiscoveryClient;
import software.amazon.awssdk.iot.discovery.DiscoveryClientConfig;
import software.amazon.awssdk.iot.discovery.model.ConnectivityInfo;
import software.amazon.awssdk.iot.discovery.model.DiscoverResponse;
import software.amazon.awssdk.iot.discovery.model.GGCore;
import software.amazon.awssdk.iot.discovery.model.GGGroup;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implementation of discovery client.
 */
public class DiscoveryClientImpl implements com.aws.greengrass.testing.mqtt5.client.DiscoveryClient {
    private static final Logger logger = LogManager.getLogger(DiscoveryClientImpl.class);

    @Override
    public CoreDeviceDiscoveryReply discoveryCoreDevice(CoreDeviceDiscoveryRequest request)
            throws DiscoveryException {
        try (SocketOptions socketOptions = new SocketOptions();
                TlsContextOptions tlsOptions = TlsContextOptions.createWithMtls(request.getCert(), request.getKey())
                                                             .withCertificateAuthority(request.getCa())
                                                             .withAlpnList(DiscoveryClient.TLS_EXT_ALPN);
                DiscoveryClientConfig config = new DiscoveryClientConfig(tlsOptions, socketOptions, request.getRegion(),
                                                                            1, null);
                DiscoveryClient client = new DiscoveryClient(config)) {
            CompletableFuture<DiscoverResponse> discoverFuture = client.discover(request.getThingName());
            try {
                DiscoverResponse response = discoverFuture.get(request.getTimeout(), TimeUnit.SECONDS);
                return convertResponseToReply(response);
            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                logger.atError().withThrowable(ex).log("Failed during discover");
                throw new DiscoveryException("Could not do discovery", ex);
            }
        }
    }

    private CoreDeviceDiscoveryReply convertResponseToReply(final DiscoverResponse response)
            throws DiscoveryException {
        if (response == null) {
            throw new DiscoveryException("Discovery response is missing");
        }

        final List<GGGroup> groups = response.getGGGroups();
        if (groups == null || groups.isEmpty() || groups.get(0) == null) {
            throw new DiscoveryException("Groups are missing in discovery response");
        }

        CoreDeviceDiscoveryReply.Builder builder = CoreDeviceDiscoveryReply.newBuilder();
        for (final GGGroup group : groups) {
            List<String> ca = group.getCAs();
            logger.atInfo().log("Discovered groupId {} with {} CA", group.getGGGroupId(), ca.size());
            CoreDeviceGroup.Builder groupBuiler = CoreDeviceGroup.newBuilder();
            groupBuiler.addAllCaList(ca);

            for (final GGCore core : group.getCores()) {
                logger.atInfo().log("Discovered Core with thing Arn {}", core.getThingArn());
                for (final ConnectivityInfo ci : core.getConnectivity()) {
                    logger.atInfo().log("Discovered connectivity info: id {} host {} port {}", ci.getId(),
                                        ci.getHostAddress(), ci.getPortNumber());

                    CoreDeviceConnectivityInfo cdc = CoreDeviceConnectivityInfo.newBuilder()
                                                        .setHost(ci.getHostAddress())
                                                        .setPort(ci.getPortNumber())
                                                        .build();

                    groupBuiler.addConnectivityInfoList(cdc);
                }
            }
            builder.addGroupList(groupBuiler.build());
        }

        return builder.build();
    }
}
