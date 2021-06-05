/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.cisclient.CISClient;
import org.bouncycastle.asn1.x500.X500Name;
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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;

import static com.aws.greengrass.certificatemanager.certificate.CertificateGenerator.DEFAULT_CERT_EXPIRY_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@ExtendWith({MockitoExtension.class})
public class CertificateExpiryMonitorTest {
    private static final String TEST_PASSPHRASE = "testPassphrase";
    private static final String SUBJECT_PRINCIPAL
            = "CN=testCNC\\=USST\\=WashingtonL\\=SeattleO\\=Amazon.com Inc.OU\\=Amazon Web Services";
    private static final long TEST_CERT_EXPIRY_CHECK_SECONDS = 1;

    @Mock
    private Consumer<X509Certificate> mockCallback;

    @Mock
    private CISClient mockCISClient;

    @TempDir
    Path tmpPath;

    @Test
    public void GIVEN_certs_added_to_monitor_WHEN_expired_THEN_regenerated() throws Exception {
        CertificateStore certificateStore = new CertificateStore(tmpPath);
        certificateStore.update(TEST_PASSPHRASE, CertificateStore.CAType.RSA_2048);
        X500Name subject = new X500Name(SUBJECT_PRINCIPAL);
        PublicKey key1 = CertificateStore.newRSAKeyPair().getPublic();
        PublicKey key2 = CertificateStore.newRSAKeyPair().getPublic();

        //start cert expiry monitor
        ScheduledExecutorService ses = new ScheduledThreadPoolExecutor(1);
        CertificateExpiryMonitor certExpiryMonitor = new CertificateExpiryMonitor(ses, mockCISClient);
        certExpiryMonitor.startMonitor(TEST_CERT_EXPIRY_CHECK_SECONDS);

        //add certs to monitor
        CertificateGenerator cg1 = new ServerCertificateGenerator(subject, key1, mockCallback, certificateStore);
        cg1.generateCertificate(Collections::emptyList);
        certExpiryMonitor.addToMonitor(cg1);
        X509Certificate cert1initial = cg1.getCertificate();
        CertificateGenerator cg2 = new ServerCertificateGenerator(subject, key2, mockCallback, certificateStore);
        cg2.generateCertificate(Collections::emptyList);
        certExpiryMonitor.addToMonitor(cg2);
        X509Certificate cert2initial = cg2.getCertificate();

        //cert1 expires -> it is regenerated
        Clock mockClock = Clock.fixed(Instant.now().plus(Duration.ofSeconds(DEFAULT_CERT_EXPIRY_SECONDS)),
                ZoneId.of("UTC"));
        cg1.setClock(mockClock);
        Thread.sleep(TEST_CERT_EXPIRY_CHECK_SECONDS*1000);
        X509Certificate cert1second = cg1.getCertificate();
        assertThat(cert1second, is(not(cert1initial)));
        X509Certificate cert2second = cg2.getCertificate();
        assertThat(cert2second, is(cert2initial));

        //cert2 expires -> it is regenerated
        mockClock = Clock.fixed(Instant.now().plus(Duration.ofSeconds(DEFAULT_CERT_EXPIRY_SECONDS)), ZoneId.of("UTC"));
        cg2.setClock(mockClock);
        Thread.sleep(TEST_CERT_EXPIRY_CHECK_SECONDS*1000);
        X509Certificate cert1third = cg1.getCertificate();
        assertThat(cert1third, is(cert1second));
        X509Certificate cert2third = cg2.getCertificate();
        assertThat(cert2third, is(not(cert2second)));

        //stop monitor, certs expire -> certs not regenerated
        certExpiryMonitor.stopMonitor();
        mockClock = Clock.fixed(Instant.now().plus(Duration.ofSeconds(2*DEFAULT_CERT_EXPIRY_SECONDS)), ZoneId.of("UTC"));
        cg1.setClock(mockClock);
        cg2.setClock(mockClock);
        Thread.sleep(TEST_CERT_EXPIRY_CHECK_SECONDS*1000);
        X509Certificate cert1fourth = cg1.getCertificate();
        assertThat(cert1fourth, is(cert1third));
        X509Certificate cert2fourth = cg2.getCertificate();
        assertThat(cert2fourth, is(cert2third));
    }
}
