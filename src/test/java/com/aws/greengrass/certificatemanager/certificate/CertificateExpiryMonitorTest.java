/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.cisclient.ConnectivityInfoProvider;
import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CertificateExpiryMonitorTest {
    private static final String TEST_PASSPHRASE = "testPassphrase";
    private static final String SUBJECT_PRINCIPAL
            = "CN=testCNC\\=USST\\=WashingtonL\\=SeattleO\\=Amazon.com Inc.OU\\=Amazon Web Services";
    private static final long TEST_CERT_EXPIRY_CHECK_MILLIS = 200;

    @Mock
    private Consumer<X509Certificate> mockCallback;

    @Mock
    private ConnectivityInfoProvider mockConnectivityInfoProvider;

    private final ScheduledExecutorService ses = new ScheduledThreadPoolExecutor(1);
    private Topics configurationTopics;
    private CertificatesConfig certificatesConfig;

    @TempDir
    Path tmpPath;

    @BeforeEach
    void setup() {
        configurationTopics = Topics.of(new Context(), KernelConfigResolver.CONFIGURATION_CONFIG_KEY, null);
        certificatesConfig = new CertificatesConfig(configurationTopics);
    }

    @AfterEach
    void afterEach() throws IOException {
        ses.shutdownNow();
        configurationTopics.getContext().close();
    }

    @Test
    public void GIVEN_certs_added_to_monitor_WHEN_expired_THEN_regenerated() throws Exception {
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
        TimeUnit.MILLISECONDS.sleep(TEST_CERT_EXPIRY_CHECK_MILLIS);
        X509Certificate cert1second = cg1.getCertificate();
        assertThat(cert1second, is(not(cert1initial)));
        X509Certificate cert2second = cg2.getCertificate();
        assertThat(cert2second, is(cert2initial));

        //cert2 expires -> it is regenerated
        mockClock = Clock.fixed(Instant.now().plus(Duration.ofSeconds(CertificatesConfig.DEFAULT_SERVER_CERT_EXPIRY_SECONDS)), ZoneId.of("UTC"));
        cg2.setClock(mockClock);
        TimeUnit.MILLISECONDS.sleep(TEST_CERT_EXPIRY_CHECK_MILLIS);
        X509Certificate cert1third = cg1.getCertificate();
        assertThat(cert1third, is(cert1second));
        X509Certificate cert2third = cg2.getCertificate();
        assertThat(cert2third, is(not(cert2second)));

        //stop monitor, certs expire -> certs not regenerated
        certExpiryMonitor.stopMonitor();
        mockClock = Clock.fixed(Instant.now().plus(Duration.ofSeconds(2*CertificatesConfig.DEFAULT_SERVER_CERT_EXPIRY_SECONDS)), ZoneId.of("UTC"));
        cg1.setClock(mockClock);
        cg2.setClock(mockClock);
        TimeUnit.MILLISECONDS.sleep(TEST_CERT_EXPIRY_CHECK_MILLIS);
        X509Certificate cert1fourth = cg1.getCertificate();
        assertThat(cert1fourth, is(cert1third));
        X509Certificate cert2fourth = cg2.getCertificate();
        assertThat(cert2fourth, is(cert2third));
    }
}
