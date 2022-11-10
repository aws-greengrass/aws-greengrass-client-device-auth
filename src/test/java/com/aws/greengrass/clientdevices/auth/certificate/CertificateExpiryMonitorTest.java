/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CertificateExpiryMonitorTest {
    private static final String TEST_PASSPHRASE = "testPassphrase";
    private static final String SUBJECT_PRINCIPAL =
            "CN=testCNC\\=USST\\=WashingtonL\\=SeattleO\\=Amazon.com Inc.OU\\=Amazon Web Services";
    private static final X500Name SUBJECT = new X500Name(SUBJECT_PRINCIPAL);
    private static final Duration CERT_EXPIRY = Duration.ofDays(7);

    Topics configTopics;
    CertificatesConfig certificatesConfig;
    CertificateStore certificateStore;
    CertificateExpiryMonitor certExpiryMonitor;

    @TempDir
    Path tmpPath;

    @BeforeEach
    void setup() throws KeyStoreException {
        configTopics = Topics.of(new Context(), KernelConfigResolver.CONFIGURATION_CONFIG_KEY, null);
        certificatesConfig = new CertificatesConfig(configTopics);

        certificateStore = new CertificateStore(tmpPath, new DomainEvents());
        certificateStore.update(TEST_PASSPHRASE, CertificateStore.CAType.RSA_2048);

        certExpiryMonitor =
                new CertificateExpiryMonitor(mock(ScheduledExecutorService.class), mock(ConnectivityInformation.class),
                        Clock.systemUTC());
    }

    @AfterEach
    void tearDown() throws IOException {
        configTopics.getContext().close();
    }

    @Test
    void GIVEN_certs_added_to_monitor_WHEN_certs_just_expired_THEN_new_certs_are_generated() throws Exception {
        Clock now = Clock.fixed(Instant.now(), ZoneId.of("UTC"));

        CertificateGenerator serverCg = monitorNewServerCert(now);
        CertificateGenerator clientCg = monitorNewClientCert(now);

        Clock expiration = Clock.fixed(Instant.now(now).plus(CERT_EXPIRY), ZoneId.of("UTC"));

        // when certs are regenerated, make regen time the current time
        serverCg.setClock(expiration);
        clientCg.setClock(expiration);

        // expire and regenerate all certs
        certExpiryMonitor.setClock(expiration);
        certExpiryMonitor.watchForCertExpiryOnce();

        // verify certs are new
        assertEquals(expiration.instant().plus(CERT_EXPIRY).truncatedTo(ChronoUnit.SECONDS), serverCg.getExpiryTime());
        assertEquals(expiration.instant().plus(CERT_EXPIRY).truncatedTo(ChronoUnit.SECONDS), clientCg.getExpiryTime());
    }

    @Test
    void GIVEN_certs_added_to_monitor_WHEN_certs_expired_THEN_new_certs_are_generated() throws Exception {
        Clock now = Clock.fixed(Instant.now(), ZoneId.of("UTC"));

        CertificateGenerator serverCg = monitorNewServerCert(now);
        CertificateGenerator clientCg = monitorNewClientCert(now);

        Clock pastExpiration =
                Clock.fixed(Instant.now(now).plus(CERT_EXPIRY).plus(10, ChronoUnit.DAYS), ZoneId.of("UTC"));

        // when certs are regenerated, make regen time the current time
        serverCg.setClock(pastExpiration);
        clientCg.setClock(pastExpiration);

        // expire and regenerate all certs
        certExpiryMonitor.setClock(pastExpiration);
        certExpiryMonitor.watchForCertExpiryOnce();

        // verify certs are new
        assertEquals(pastExpiration.instant().plus(CERT_EXPIRY).truncatedTo(ChronoUnit.SECONDS),
                serverCg.getExpiryTime());
        assertEquals(pastExpiration.instant().plus(CERT_EXPIRY).truncatedTo(ChronoUnit.SECONDS),
                clientCg.getExpiryTime());
    }

    @Test
    void GIVEN_certs_added_to_monitor_WHEN_certs_approaching_expiration_THEN_new_certs_are_generated()
            throws Exception {
        Clock now = Clock.fixed(Instant.now(), ZoneId.of("UTC"));

        CertificateGenerator serverCg = monitorNewServerCert(now);
        CertificateGenerator clientCg = monitorNewClientCert(now);

        Clock approachingExpiration =
                Clock.fixed(Instant.now(now).plus(CERT_EXPIRY).minus(1, ChronoUnit.DAYS), ZoneId.of("UTC"));

        // when certs are regenerated, make regen time the current time
        serverCg.setClock(approachingExpiration);
        clientCg.setClock(approachingExpiration);

        // expire and regenerate all certs
        certExpiryMonitor.setClock(approachingExpiration);
        certExpiryMonitor.watchForCertExpiryOnce();

        // verify certs are new
        assertEquals(approachingExpiration.instant().plus(CERT_EXPIRY).truncatedTo(ChronoUnit.SECONDS),
                serverCg.getExpiryTime());
        assertEquals(approachingExpiration.instant().plus(CERT_EXPIRY).truncatedTo(ChronoUnit.SECONDS),
                clientCg.getExpiryTime());
    }

    @Test
    void GIVEN_certs_added_to_monitor_WHEN_certs_not_near_expiration_THEN_no_certs_are_generated() throws Exception {
        Clock now = Clock.fixed(Instant.now(), ZoneId.of("UTC"));

        CertificateGenerator serverCg = monitorNewServerCert(now);
        CertificateGenerator clientCg = monitorNewClientCert(now);

        Instant originalServerCertExpiry = serverCg.getExpiryTime();
        Instant originalClientCertExpiry = clientCg.getExpiryTime();

        Clock notExpired = Clock.fixed(
                Instant.now(now).plus(Duration.ofSeconds(CertificatesConfig.DEFAULT_SERVER_CERT_EXPIRY_SECONDS))
                        .minus(2, ChronoUnit.DAYS), ZoneId.of("UTC"));

        // when certs are regenerated, make regen time the current time
        serverCg.setClock(notExpired);
        clientCg.setClock(notExpired);

        // attempt to expire and regenerate all certs
        certExpiryMonitor.setClock(notExpired);
        certExpiryMonitor.watchForCertExpiryOnce();

        // verify no certs were created
        assertEquals(originalServerCertExpiry, serverCg.getExpiryTime());
        assertEquals(originalClientCertExpiry, clientCg.getExpiryTime());
    }

    /**
     * Create a new server certificate generator and add it to the expiry monitor.
     *
     * @param clock clock
     * @return cert generator
     * @throws NoSuchAlgorithmException if unable to generate an RSA key pair
     * @throws KeyStoreException        if unable to generate certificate
     */
    private CertificateGenerator monitorNewServerCert(Clock clock)
            throws NoSuchAlgorithmException, CertificateGenerationException {
        return monitorNewCert(key -> new ServerCertificateGenerator(SUBJECT, key, (cert, caChain) -> {
        }, certificateStore, certificatesConfig, clock));
    }

    /**
     * Create a new client certificate generator and add it to the expiry monitor.
     *
     * @param clock clock
     * @return cert generator
     * @throws NoSuchAlgorithmException if unable to generate an RSA key pair
     * @throws KeyStoreException        if unable to generate certificate
     */
    private CertificateGenerator monitorNewClientCert(Clock clock)
            throws NoSuchAlgorithmException, CertificateGenerationException {
        return monitorNewCert(key -> new ClientCertificateGenerator(SUBJECT, key, (cert, caChain) -> {
        }, certificateStore, certificatesConfig, clock));
    }

    private CertificateGenerator monitorNewCert(Function<PublicKey, CertificateGenerator> cgFactory)
            throws NoSuchAlgorithmException, CertificateGenerationException {
        PublicKey key = CertificateStore.newRSAKeyPair().getPublic();
        CertificateGenerator cg = cgFactory.apply(key);
        cg.generateCertificate(Collections::emptyList, "test");
        certExpiryMonitor.addToMonitor(cg);
        return cg;
    }
}
