/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequest;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.CertificatesConfig;
import com.aws.greengrass.clientdevices.auth.certificate.handlers.CertificateRotationHandler;
import com.aws.greengrass.clientdevices.auth.connectivity.CISShadowMonitor;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.CertificateSubscriptionHandler;
import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.telemetry.impl.Metric;
import com.aws.greengrass.telemetry.models.TelemetryAggregation;
import com.aws.greengrass.telemetry.models.TelemetryUnit;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.TestUtils;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class MetricsTest {
    @Mock
    private ConnectivityInformation mockConnectivityInformation;

    @Mock
    private CertificateExpiryMonitor mockCertExpiryMonitor;

    @Mock
    private CISShadowMonitor mockShadowMonitor;

    @Mock
    private SecurityService mockSecurityService;

    @Mock
    private GreengrassServiceClientFactory mockClientFactory;

    @TempDir
    Path tmpPath;

    private CertificatesConfig certificatesConfig;
    private Topics configurationTopics;
    private CertificateManager certificateManager;
    private CertificateStore certificateStore;
    private ClientDeviceAuthMetrics metrics;
    private CertificateSubscriptionHandler certificateSubscriptionHandler;
    private CertificateRotationHandler certRotationMonitor;
    private Clock clock;

    @BeforeEach
    void beforeEach() {
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        DomainEvents domainEvents = new DomainEvents();
        metrics = new ClientDeviceAuthMetrics(clock);
        certificateSubscriptionHandler = new CertificateSubscriptionHandler(domainEvents, metrics);
        certificateSubscriptionHandler.listen();
        certificateStore = new CertificateStore(tmpPath, domainEvents, mockSecurityService);
        certRotationMonitor = new CertificateRotationHandler(mockConnectivityInformation, domainEvents);

        certificateManager = new CertificateManager(certificateStore, mockConnectivityInformation,
                mockCertExpiryMonitor, mockShadowMonitor, Clock.systemUTC(), mockClientFactory, mockSecurityService,
                certRotationMonitor, domainEvents);

        configurationTopics = Topics.of(new Context(), KernelConfigResolver.CONFIGURATION_CONFIG_KEY, null);
        certificatesConfig = new CertificatesConfig(configurationTopics);

        certificateManager.updateCertificatesConfiguration(certificatesConfig);
    }

    @AfterEach
    void afterEach() throws IOException {
        configurationTopics.getContext().close();
    }

    @Test
    void GIVEN_defaultCertManager_WHEN_subscribeToCertificateUpdates_THEN_subscribeSuccessMetricCorrectlyEmitted()
            throws KeyStoreException, CertificateGenerationException {
        certificateManager.generateCA("", CertificateStore.CAType.RSA_2048);

        Pair<CompletableFuture<Void>, Consumer<CertificateUpdateEvent>> con = TestUtils.asyncAssertOnConsumer((a) -> {
        }, 3);

        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(GetCertificateRequestOptions.CertificateType.SERVER);
        GetCertificateRequest certificateRequest =
                new GetCertificateRequest("testService", requestOptions, con.getRight());

        // Subscribe multiple times to ensure the Cert.SubscribeSuccess metric increments correctly
        certificateManager.subscribeToCertificateUpdates(certificateRequest);
        certificateManager.subscribeToCertificateUpdates(certificateRequest);
        certificateManager.subscribeToCertificateUpdates(certificateRequest);

        // Checking that the emitter collects the metrics as expected
        Metric metric = Metric.builder()
                .namespace("aws.greengrass.clientdevices.Auth")
                .name(ClientDeviceAuthMetrics.METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(3L)
                .timestamp(Instant.now(clock).toEpochMilli())
                .build();

        List<Metric> collectedMetrics = metrics.collectMetrics();
        Metric subscribeSuccess = collectedMetrics.get(0);

        assertEquals(metric.getValue(), subscribeSuccess.getValue());
        assertEquals(metric.getName(), subscribeSuccess.getName());
        assertEquals(metric.getTimestamp(), subscribeSuccess.getTimestamp());
        assertEquals(metric.getAggregation(), subscribeSuccess.getAggregation());
        assertEquals(metric.getUnit(), subscribeSuccess.getUnit());
        assertEquals(metric.getNamespace(), subscribeSuccess.getNamespace());
    }

}
