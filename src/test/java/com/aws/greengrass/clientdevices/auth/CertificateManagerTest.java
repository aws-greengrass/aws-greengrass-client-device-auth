/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequest;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.certificate.CISShadowMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.CertificatesConfig;
import com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.iot.ConnectivityInfoProvider;
import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.TestUtils;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CertificateManagerTest {
    @Mock
    ConnectivityInfoProvider mockConnectivityInfoProvider;

    @Mock
    CertificateExpiryMonitor mockCertExpiryMonitor;

    @Mock
    CISShadowMonitor mockShadowMonitor;

    @Mock
    GreengrassServiceClientFactory clientFactory;


    @TempDir
    Path tmpPath;

    private CertificateManager certificateManager;

    @BeforeEach
    void beforeEach() throws KeyStoreException, CertificateEncodingException, IOException {
        certificateManager = new CertificateManager(new CertificateStore(tmpPath), mockConnectivityInfoProvider,
                mockCertExpiryMonitor, mockShadowMonitor, Clock.systemUTC(), clientFactory);

        CertificatesConfig certificatesConfig = new CertificatesConfig(
                Topics.of(new Context(), KernelConfigResolver.CONFIGURATION_CONFIG_KEY, null));
        CAConfiguration caConfiguration = new CAConfiguration(
                Topics.of(new Context(), KernelConfigResolver.CONFIGURATION_CONFIG_KEY, null));

        certificateManager.updateCertificatesConfiguration(certificatesConfig);
        certificateManager.updateCAConfiguration(caConfiguration);
        certificateManager.update("");
    }

    @Test
    void GIVEN_defaultCertManager_WHEN_getCACertificates_THEN_singleCAReturned()
            throws CertificateEncodingException, KeyStoreException, IOException {
        List<String> caPemList = certificateManager.getCACertificates();
        Assertions.assertEquals(1, caPemList.size(), "expected single CA certificate");
    }

    @Test
    void GIVEN_defaultCertManager_WHEN_subscribeToCertificateUpdates_THEN_certificateReceived()
            throws InterruptedException, ExecutionException, TimeoutException, CertificateGenerationException {
        Pair<CompletableFuture<Void>, Consumer<CertificateUpdateEvent>> con =
                TestUtils.asyncAssertOnConsumer((a) -> {}, 3);

        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(GetCertificateRequestOptions.CertificateType.SERVER);
        GetCertificateRequest certificateRequest =
                new GetCertificateRequest("testService", requestOptions, con.getRight());

        // Subscribe multiple times to show that a new certificate is generated on each call
        certificateManager.subscribeToCertificateUpdates(certificateRequest);
        certificateManager.subscribeToCertificateUpdates(certificateRequest);
        certificateManager.subscribeToCertificateUpdates(certificateRequest);
        con.getLeft().get(1, TimeUnit.SECONDS);
    }

    @Test
    void GIVEN_serverCertRequest_WHEN_serverCertificateIsGenerated_THEN_canSuccessfullyImportToKeyStore()
            throws CertificateGenerationException {
        Consumer<CertificateUpdateEvent> cb = t -> {
            try {
                X509Certificate[] certChain = Stream.concat(
                                Stream.of(t.getCertificate()),
                                Arrays.stream(t.getCaCertificates()))
                        .toArray(X509Certificate[]::new);
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null, null);
                ks.setKeyEntry("key", t.getKeyPair().getPrivate(), "".toCharArray(), certChain);
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
                Assertions.fail(e);
            }
        };

        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(GetCertificateRequestOptions.CertificateType.SERVER);
        GetCertificateRequest certificateRequest =
                new GetCertificateRequest("testService", requestOptions, cb);

        certificateManager.subscribeToCertificateUpdates(certificateRequest);
    }

    @Test
    void GIVEN_clientCertRequest_WHEN_clientCertificateIsGenerated_THEN_canSuccessfullyImportToKeyStore()
            throws CertificateGenerationException {
        Consumer<CertificateUpdateEvent> cb = t -> {
            try {
                X509Certificate[] certChain = Stream.concat(
                                Stream.of(t.getCertificate()),
                                Arrays.stream(t.getCaCertificates()))
                        .toArray(X509Certificate[]::new);
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null, null);
                ks.setKeyEntry("key", t.getKeyPair().getPrivate(), "".toCharArray(), certChain);
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
                Assertions.fail(e);
            }
        };

        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(GetCertificateRequestOptions.CertificateType.CLIENT);
        GetCertificateRequest certificateRequest =
                new GetCertificateRequest("testService", requestOptions, cb);

        certificateManager.subscribeToCertificateUpdates(certificateRequest);
    }

    @Test
    void GIVEN_nullRequest_WHEN_subscribeToCertificateUpdates_THEN_throwsNPE() {
        Assertions.assertThrows(NullPointerException.class, () ->
                certificateManager.subscribeToCertificateUpdates(null));
    }
}
