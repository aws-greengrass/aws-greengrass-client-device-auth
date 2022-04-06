/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.cisclient.ConnectivityInfoProvider;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CertificateExpiryMonitorTest {
    private static final String TEST_PASSPHRASE = "testPassphrase";
    private static final String SUBJECT_PRINCIPAL
            = "CN=testCNC\\=USST\\=WashingtonL\\=SeattleO\\=Amazon.com Inc.OU\\=Amazon Web Services";

    @Mock
    private Consumer<X509Certificate> mockCallback;

    @Mock
    private ConnectivityInfoProvider mockConnectivityInfoProvider;

    @Mock
    private Topics mockConfigurationTopics;

    @Mock
    private ScheduledExecutorService ses;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private CertificateExpiryMonitor certExpiryMonitor;

    private CertificatesConfig certificatesConfig;

    @TempDir
    Path tmpPath;

    @BeforeEach
    void setup() {
        certExpiryMonitor = new CertificateExpiryMonitor(ses, mockConnectivityInfoProvider);
        certExpiryMonitor.startMonitor(Duration.ofMillis(100));
        certificatesConfig = new CertificatesConfig(mockConfigurationTopics);
    }

    @AfterEach
    void shutdown() {
        certExpiryMonitor.stopMonitor();
    }

    @Test
    public void GIVEN_certs_added_to_monitor_WHEN_expired_THEN_regenerated() throws Exception {
        when(mockConfigurationTopics.findOrDefault(any(), any())).thenReturn(CertificatesConfig.DEFAULT_SERVER_CERT_EXPIRY_SECONDS);
        doReturn(scheduledFuture).when(ses).scheduleAtFixedRate(any(), anyLong(), anyLong(), any(TimeUnit.class));
        CertificateStore certificateStore = new CertificateStore(tmpPath);
        certificateStore.update(TEST_PASSPHRASE, CertificateStore.CAType.RSA_2048);
        X500Name subject = new X500Name(SUBJECT_PRINCIPAL);
        PublicKey key1 = CertificateStore.newRSAKeyPair().getPublic();
        PublicKey key2 = CertificateStore.newRSAKeyPair().getPublic();

        //start cert expiry monitor
        CertificateExpiryMonitor certExpiryMonitor = new CertificateExpiryMonitor(ses, mockConnectivityInfoProvider);
        certExpiryMonitor.startMonitor(Duration.ofMillis(100));

        //add certs to monitor
        CertificateGenerator cg1 = new ServerCertificateGenerator(subject, key1, mockCallback, certificateStore, certificatesConfig);
        cg1.generateCertificate(Collections::emptyList);
        certExpiryMonitor.addToMonitor(cg1);
        X509Certificate cert1initial = cg1.getCertificate();
        CertificateGenerator cg2 = new ServerCertificateGenerator(subject, key2, mockCallback, certificateStore, certificatesConfig);
        cg2.generateCertificate(Collections::emptyList);
        certExpiryMonitor.addToMonitor(cg2);
        X509Certificate cert2initial = cg2.getCertificate();

        //cert1 expires -> it is regenerated
        Clock mockClock = Clock.fixed(Instant.now().plus(Duration.ofSeconds(CertificatesConfig.DEFAULT_SERVER_CERT_EXPIRY_SECONDS)),
                ZoneId.of("UTC"));
        cg1.setClock(mockClock);
        certExpiryMonitor.watchForCertExpiryOnce();
        X509Certificate cert1second = cg1.getCertificate();
        assertThat(cert1second, not(cert1initial));
        X509Certificate cert2second = cg2.getCertificate();
        assertThat(cert2second, is(cert2initial));

        //cert2 expires -> it is regenerated
        mockClock = Clock.fixed(Instant.now().plus(Duration.ofSeconds(CertificatesConfig.DEFAULT_SERVER_CERT_EXPIRY_SECONDS)), ZoneId.of("UTC"));
        cg2.setClock(mockClock);
        certExpiryMonitor.watchForCertExpiryOnce();
        X509Certificate cert1third = cg1.getCertificate();
        assertThat(cert1third, is(cert1second));
        X509Certificate cert2third = cg2.getCertificate();
        assertThat(cert2third, is(not(cert2second)));

        //stop monitor, certs expire -> certs not regenerated
        certExpiryMonitor.stopMonitor();
        verify(scheduledFuture).cancel(true);

        mockClock = Clock.fixed(Instant.now().plus(Duration.ofSeconds(2*CertificatesConfig.DEFAULT_SERVER_CERT_EXPIRY_SECONDS)), ZoneId.of("UTC"));
        cg1.setClock(mockClock);
        cg2.setClock(mockClock);

        // watchForCertExpiryOnce() is not called because certExpiryMonitor is stopped.

        X509Certificate cert1fourth = cg1.getCertificate();
        assertThat(cert1fourth, is(cert1third));
        X509Certificate cert2fourth = cg2.getCertificate();
        assertThat(cert2fourth, is(cert2third));
    }

    @Test
    public void GIVEN_certs_with_max_duration_WHEN_time_greater_than_default_THEN_not_generated() throws Exception {
        when(mockConfigurationTopics.findOrDefault(any(), any())).thenReturn(CertificatesConfig.MAX_SERVER_CERT_EXPIRY_SECONDS);
        CertificateStore certificateStore = new CertificateStore(tmpPath);
        certificateStore.update(TEST_PASSPHRASE, CertificateStore.CAType.RSA_2048);
        X500Name subject = new X500Name(SUBJECT_PRINCIPAL);
        PublicKey key1 = CertificateStore.newRSAKeyPair().getPublic();

        // Add certificate to monitor
        CertificateGenerator cg1 = new ServerCertificateGenerator(subject, key1, mockCallback, certificateStore, certificatesConfig);
        cg1.generateCertificate(Collections::emptyList);
        certExpiryMonitor.addToMonitor(cg1);
        X509Certificate cert1initial = cg1.getCertificate();

        //Add DEFAULT_SERVER_CERT_EXPIRY_SECONDS time. cert1 should not expire.
        Clock mockClock = Clock.fixed(Instant.now().plus(Duration.ofSeconds(CertificatesConfig.DEFAULT_SERVER_CERT_EXPIRY_SECONDS)),
                ZoneId.of("UTC"));
        cg1.setClock(mockClock);
        certExpiryMonitor.watchForCertExpiryOnce();
        X509Certificate cert1second = cg1.getCertificate();
        assertThat(cert1second, is(cert1initial));


        //Add MAX_SERVER_CERT_EXPIRY_SECONDS. cert1 should expire and generate a new cert
        mockClock = Clock.fixed(Instant.now().plus(Duration.ofSeconds(CertificatesConfig.MAX_SERVER_CERT_EXPIRY_SECONDS)),
                ZoneId.of("UTC"));
        cg1.setClock(mockClock);
        certExpiryMonitor.watchForCertExpiryOnce();
        X509Certificate cert1third = cg1.getCertificate();
        assertThat(cert1third, not(cert1initial));

        // Verify that we only get server cert validity twice. Once for initial cert and the other for regeneration.
        // This is only called during server cert generation.
        verify(mockConfigurationTopics, times(2)).findOrDefault(any(),any());
    }
}
