/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.certificateauthority;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.infra.ClientCertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.infra.BackgroundCertificateRefresh;
import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.connectivity.CISShadowMonitor;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.infra.NetworkState;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;

import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers.createClientCertificate;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class OfflineAuthenticationTest {
    @Mock
    SecurityService securityServiceMock;
    @Mock
    private GreengrassServiceClientFactory clientFactory;
    @Mock
    private GroupManager groupManager;
    @Mock
    CertificateExpiryMonitor certExpiryMonitorMock;
    @Mock
    CISShadowMonitor cisShadowMonitorMock;
    @Mock
    private GreengrassV2DataClient client;
    @Mock
    private NetworkState networkStateMock;
    @TempDir
    Path rootDir;
    private Kernel kernel;
    private IotAuthClientFake iotAuthClientFake;


    @BeforeEach
    void setup(ExtensionContext context) throws DeviceConfigurationException {
        ignoreExceptionOfType(context, SpoolerStoreException.class);

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();
        kernel.getContext().put(NetworkState.class, networkStateMock);
        kernel.getContext().put(GroupManager.class, groupManager);
        kernel.getContext().put(SecurityService.class, securityServiceMock);
        kernel.getContext().put(CertificateExpiryMonitor.class, certExpiryMonitorMock);
        kernel.getContext().put(CISShadowMonitor.class, cisShadowMonitorMock);
        kernel.getContext().put(GreengrassServiceClientFactory.class, clientFactory);

        DomainEvents domainEvents =  new DomainEvents();
        CertificateStore certificateStore = new CertificateStore(rootDir, domainEvents , securityServiceMock);

        kernel.getContext().put(DomainEvents.class, domainEvents);
        kernel.getContext().put(CertificateStore.class, certificateStore);

        iotAuthClientFake = new IotAuthClientFake();
        kernel.getContext().put(IotAuthClient.class, iotAuthClientFake);

        lenient().when(clientFactory.fetchGreengrassV2DataClient()).thenReturn(client);
    }

    @AfterEach
    void cleanup() {
        kernel.shutdown();
    }

    private Runnable mockInstant(long expected) {
        Clock spyClock = spy(Clock.class);
        MockedStatic<Clock> clockMock;
        clockMock = mockStatic(Clock.class);
        clockMock.when(Clock::systemUTC).thenReturn(spyClock);
        when(spyClock.instant()).thenReturn(Instant.ofEpochMilli(expected));

        return clockMock::close;
    }

    private void givenNucleusRunningWithConfig(String configFileName) throws InterruptedException {
        CountDownLatch authServiceRunning = new CountDownLatch(1);
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        kernel.getContext().addGlobalStateChangeListener((service, was, newState) -> {
            if (ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME.equals(service.getName()) && service.getState()
                    .equals(State.RUNNING)) {
                authServiceRunning.countDown();
            }
        });
        kernel.launch();
        assertThat(authServiceRunning.await(30L, TimeUnit.SECONDS), is(true));
    }

    @Test
    void GIVEN_clientDevice_WHEN_verifyingItsIdentity_THEN_pemStored() throws InterruptedException,
            NoSuchAlgorithmException, CertificateException, OperatorCreationException, IOException,
            InvalidCertificateException {
        // Given
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);
        KeyPair clientKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate clientCert = createClientCertificate(
                rootCA, "AWS IoT Certificate", clientKeyPair.getPublic(), rootKeyPair.getPrivate());

        String clientCertPem = CertificateHelper.toPem(clientCert);
        iotAuthClientFake.activateCert(clientCertPem);
        givenNucleusRunningWithConfig("config.yaml");

        // When
       ClientDevicesAuthServiceApi api = kernel.getContext().get(ClientDevicesAuthServiceApi.class);
       boolean verifyResult = api.verifyClientDeviceIdentity(clientCertPem);

       // Then
       assertTrue(verifyResult);

       String clientCertId = Certificate.fromPem(clientCertPem).getCertificateId();
       ClientCertificateStore store = kernel.getContext().get(ClientCertificateStore.class);
       String storedPem = store.getPem(clientCertId).get();

       assertEquals(storedPem, clientCertPem);
    }

   @Test
   void GIVEN_storedCertificates_WHEN_refreshEnabled_THEN_storedCertificatesRefreshed() throws
           NoSuchAlgorithmException, CertificateException, OperatorCreationException, IOException,
           InvalidCertificateException, InterruptedException {
       Instant now = Instant.now();
       Runnable resetClock = mockInstant(now.toEpochMilli());

       // Given
       givenCoreDeviceIsOnline();
       List<String> clientDevicePems = givenClientDevicesWithCertificates(2);

       // When
       givenNucleusRunningWithConfig("config.yaml");
       givenClientDevicesVerifyTheirIdentity(clientDevicePems, now);

       Instant anHourLater = now.plusSeconds(60 * 60);
       resetClock.run();
       resetClock = mockInstant(anHourLater.toEpochMilli());

       BackgroundCertificateRefresh backgroundRefresh = kernel.getContext().get(BackgroundCertificateRefresh.class);
       assertTrue(backgroundRefresh.isRunning(), "background refresh is not running");
       backgroundRefresh.run(); // Force a run because otherwise it is controlled by a ScheduledExecutorService
       kernel.getConfig().waitConfigUpdateComplete();

       // Then
       CertificateRegistry certRegistry = kernel.getContext().get(CertificateRegistry.class);
       Certificate certA = certRegistry.getCertificateFromPem(clientDevicePems.get(0)).get();
       Certificate certB = certRegistry.getCertificateFromPem(clientDevicePems.get(1)).get();

       assertEquals(certA.getStatusLastUpdated().toEpochMilli(), anHourLater.toEpochMilli());
       assertEquals(certB.getStatusLastUpdated().toEpochMilli(), anHourLater.toEpochMilli());
       resetClock.run();
   }

    @Test
    void GIVEN_storedCertificates_WHEN_refreshEnabledAndCertificateDelete_THEN_removedCertificateNotRefreshed() throws
            NoSuchAlgorithmException, CertificateException, OperatorCreationException, IOException,
            InvalidCertificateException, InterruptedException {
        Instant now = Instant.now();
        Runnable resetClock = mockInstant(now.toEpochMilli());

        // Given
        givenCoreDeviceIsOnline();
        List<String> clientDevicePems = givenClientDevicesWithCertificates(2);
        givenNucleusRunningWithConfig("config.yaml");
        givenClientDevicesVerifyTheirIdentity(clientDevicePems, now);

        // When
        // Simulate cloud returning a response that tells CDA a certificate is not active anymore
        iotAuthClientFake.deactivateCert(clientDevicePems.get(1));

        Instant anHourLater = now.plusSeconds(60 * 60);
        resetClock.run();
        resetClock = mockInstant(anHourLater.toEpochMilli());

        BackgroundCertificateRefresh backgroundRefresh = kernel.getContext().get(BackgroundCertificateRefresh.class);
        assertTrue(backgroundRefresh.isRunning(), "background refresh is not running");
        backgroundRefresh.run(); // Force a run because otherwise it is controlled by a ScheduledExecutorService
        kernel.getConfig().waitConfigUpdateComplete();

        // Then
        CertificateRegistry certRegistry = kernel.getContext().get(CertificateRegistry.class);
        Certificate certA = certRegistry.getCertificateFromPem(clientDevicePems.get(0)).get();
        assertEquals(certA.getStatusLastUpdated().toEpochMilli(), anHourLater.toEpochMilli());

        Optional<Certificate> certB = certRegistry.getCertificateFromPem(clientDevicePems.get(1));
        assertFalse(certB.isPresent());
        resetClock.run();
    }


    /**
     * Simulates client devices validating their certificates through the CDA API.
     * @param clientDevicePems - list of client device certificate PEM.
     * @param now - An instant in time
     */
    private void givenClientDevicesVerifyTheirIdentity(List<String> clientDevicePems, Instant now) throws
            InvalidCertificateException {
        // Simulate some client components (like Moquette) verifying some certificates
        ClientDevicesAuthServiceApi api = kernel.getContext().get(ClientDevicesAuthServiceApi.class);
        CertificateRegistry certRegistry = kernel.getContext().get(CertificateRegistry.class);

        for (String pem : clientDevicePems) {
            api.verifyClientDeviceIdentity(pem);

            Certificate fromRegistry = certRegistry.getCertificateFromPem(pem).get();
            assertEquals(fromRegistry.getStatusLastUpdated().toEpochMilli(), now.toEpochMilli());
        }
    }

    /**
     * Simulates a device connectivity being up.
     */
    private void givenCoreDeviceIsOnline() {
        when(networkStateMock.getConnectionStateFromMqtt()).thenReturn(NetworkState.ConnectionState.NETWORK_UP);
    }

    /**
     * Generates a list of client certificate PEMs which represent the certificate client devices (GGADs) provide
     * to authenticate with CDA.
     * @param noGGADs - Number of client device certificates to generate
     */
    private List<String> givenClientDevicesWithCertificates(int noGGADs)
            throws NoSuchAlgorithmException, CertificateException,
            OperatorCreationException, IOException, InvalidCertificateException {
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);

        ArrayList<String> pems = new ArrayList<>(noGGADs);

        for (int i = 0; i < noGGADs; i++) {
            KeyPair clientAKeyPair = CertificateStore.newRSAKeyPair(2048);
            X509Certificate clientACrt = createClientCertificate(
                    rootCA, "AWS IoT Certificate", clientAKeyPair.getPublic(), rootKeyPair.getPrivate());

            String clientPem = CertificateHelper.toPem(clientACrt);
            iotAuthClientFake.activateCert(clientPem);
            pems.add(clientPem);
        }

        return pems;
    }

}