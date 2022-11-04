/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.certificateauthority;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.infra.ClientCertificateStore;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.helpers.ClockFake;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.clientdevices.auth.iot.NetworkStateFake;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;

import com.aws.greengrass.util.NucleusPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@ExtendWith({MockitoExtension.class, GGExtension.class})
public class OfflineAuthenticationTest {
    @TempDir
    Path rootDir;
    private Kernel kernel;
    private IotAuthClientFake iotAuthClientFake;
    private NetworkStateFake network;
    private ClockFake clock;


    @BeforeEach
    void setup(ExtensionContext context) {
        ignoreExceptionOfType(context, SpoolerStoreException.class);

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();
        network = new NetworkStateFake();
        kernel.getContext().put(NetworkStateProvider.class, network);

        DomainEvents domainEvents = new DomainEvents();
        kernel.getContext().put(DomainEvents.class, domainEvents);

        clock = new ClockFake();
        iotAuthClientFake = new IotAuthClientFake(clock);
        kernel.getContext().put(IotAuthClient.class, iotAuthClientFake);
        kernel.getContext().put(Clock.class, clock);
    }

    @AfterEach
    void cleanup() {
        kernel.shutdown();
    }

    private void runNucleusWithConfig(String configFileName) throws InterruptedException {
        CountDownLatch authServiceRunning = new CountDownLatch(1);
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        kernel.getContext().addGlobalStateChangeListener((service, was, newState) -> {
            if (ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME.equals(service.getName())
                    && service.getState().equals(State.RUNNING)) {
                authServiceRunning.countDown();
            }
        });
        kernel.launch();
        assertThat(authServiceRunning.await(30L, TimeUnit.SECONDS), is(true));
    }

    private String connectToCore(String thingName, String certificatePem) throws AuthenticationException {
        ClientDevicesAuthServiceApi api = kernel.getContext().get(ClientDevicesAuthServiceApi.class);
        // Simulate some client components (like Moquette) verifying some certificates
        api.verifyClientDeviceIdentity(certificatePem);
        // Simulate a client connecting and generating a session
        return api.getClientDeviceAuthToken("mqtt", new HashMap<String, String>() {{
            put("clientId", thingName);
            put("certificatePem", certificatePem);
            put("username", "foo");
            put("password", "bar");
        }});
    }

    private void corruptStoredClientCertificate(String pem) throws InvalidCertificateException, IOException {
        NucleusPaths paths = kernel.getNucleusPaths();
        Path workPath = paths.workPath(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME);
        Certificate cert = Certificate.fromPem(pem);
        Path pemFilePath = workPath.resolve("clients").resolve(cert.getCertificateId() + ".pem");

        try (OutputStream writeStream = Files.newOutputStream(pemFilePath)) {
            writeStream.write("I am evil :)".getBytes());
        }
    }

    @Test
    void GIVEN_clientDevice_WHEN_verifyingItsIdentity_THEN_pemStored(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, NoSuchFileException.class);
        // Given
        List<X509Certificate> clientCertificates = CertificateTestHelpers.createClientCertificates(1);

        String clientCertPem = CertificateHelper.toPem(clientCertificates.get(0));
        iotAuthClientFake.activateCert(clientCertPem);
        runNucleusWithConfig("config.yaml");

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
    void GIVEN_clientConnectsWhileOnline_WHEN_offline_THEN_clientCanConnect(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, NoSuchFileException.class);

        // Given
        network.goOnline();

        // Configure the things attached to the core
        List<X509Certificate> clientCertificates = CertificateTestHelpers.createClientCertificates(1);
        String clientPem = CertificateHelper.toPem(clientCertificates.get(0));
        Supplier<String> thingOne = () -> "ThingOne";
        iotAuthClientFake.attachCertificateToThing(thingOne.get(), clientPem);
        iotAuthClientFake.attachThingToCore(thingOne);
        iotAuthClientFake.activateCert(clientPem);

        runNucleusWithConfig("config.yaml");
        assertNotNull(connectToCore(thingOne.get(), clientPem));

        // When
        network.goOffline();

        // Then
        assertNotNull(connectToCore(thingOne.get(), clientPem));
    }

    @Test
    void GIVEN_clientConnectsWhileOnline_WHEN_offlineAndTtlExpired_THEN_clientCanNotConnect(ExtensionContext context)
            throws Exception {
        ignoreExceptionOfType(context, NoSuchFileException.class);

        // Given
        network.goOnline();
        Instant now = Instant.now();
        clock.setCurrentInstant(now);

        // Configure the things attached to the core
        List<X509Certificate> clientCertificates = CertificateTestHelpers.createClientCertificates(1);
        String clientPem = CertificateHelper.toPem(clientCertificates.get(0));
        Supplier<String> thingOne = () -> "ThingOne";
        iotAuthClientFake.attachCertificateToThing(thingOne.get(), clientPem);
        iotAuthClientFake.attachThingToCore(thingOne);
        iotAuthClientFake.activateCert(clientPem);

        runNucleusWithConfig("config.yaml");
        assertNotNull(connectToCore(thingOne.get(), clientPem));

        // When
        network.goOffline();
        Instant twoMinutesLater = now.plus(Duration.ofMinutes(2)); // Default expiry is 1 minute
        clock.setCurrentInstant(twoMinutesLater);


        // Then
        assertThrows(AuthenticationException.class, () -> {
            connectToCore(thingOne.get(), clientPem);
        });
    }

    @Test
    void GIVEN_clientConnectsWhileOnline_WHEN_offlineAndCertificateRevoked_THEN_backOnlineAndClientRejected(
            ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, NoSuchFileException.class);
        // Given
        network.goOnline();

        // Configure the things attached to the core
        List<X509Certificate> clientCertificates = CertificateTestHelpers.createClientCertificates(1);
        String clientPem = CertificateHelper.toPem(clientCertificates.get(0));
        Supplier<String> thingOne = () -> "ThingOne";
        iotAuthClientFake.attachCertificateToThing(thingOne.get(), clientPem);
        iotAuthClientFake.attachThingToCore(thingOne);
        iotAuthClientFake.activateCert(clientPem);

        runNucleusWithConfig("config.yaml");
        assertNotNull(connectToCore(thingOne.get(), clientPem));

        // When
        network.goOffline();
        iotAuthClientFake.deactivateCert(clientPem); // Revoked
        assertNotNull(connectToCore(thingOne.get(), clientPem));

        // Then
        network.goOnline();
        assertThrows(AuthenticationException.class, () -> {
            connectToCore(thingOne.get(), clientPem);
        });
    }

    @Test
    void GIVEN_clientConnectsWhileOnline_WHEN_offlineAndCertDetachedFromThing_THEN_backOnlineAndClientRejected(
            ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, NoSuchFileException.class);
        // Given
        network.goOnline();

        // Configure the things attached to the core
        List<X509Certificate> clientCertificates = CertificateTestHelpers.createClientCertificates(1);
        String clientPem = CertificateHelper.toPem(clientCertificates.get(0));
        Supplier<String> thingOne = () -> "ThingOne";
        iotAuthClientFake.attachCertificateToThing(thingOne.get(), clientPem);
        iotAuthClientFake.attachThingToCore(thingOne);
        iotAuthClientFake.activateCert(clientPem);

        runNucleusWithConfig("config.yaml");
        assertNotNull(connectToCore(thingOne.get(), clientPem));

        // When
        network.goOffline();
        iotAuthClientFake.detachCertificateFromThing(thingOne.get(), clientPem);  // Detached
        assertNotNull(connectToCore(thingOne.get(), clientPem));

        // Then
        network.goOnline();
        assertThrows(AuthenticationException.class, () -> {
            connectToCore(thingOne.get(), clientPem);
        });
    }

    @Test
    void GIVEN_clientConnectsWhileOnline_WHEN_storedPemIsCorrupted_THEN_clientCanStillConnectOnlineOrOffline(
            ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, NoSuchFileException.class);
        ignoreExceptionOfType(context, InvalidCertificateException.class);
        // Given
        network.goOnline();

        // Configure the things attached to the core
        List<X509Certificate> clientCertificates = CertificateTestHelpers.createClientCertificates(1);
        String clientPem = CertificateHelper.toPem(clientCertificates.get(0));
        Certificate certificate = Certificate.fromPem(clientPem);
        Supplier<String> thingOne = () -> "ThingOne";
        iotAuthClientFake.attachCertificateToThing(thingOne.get(), clientPem);
        iotAuthClientFake.attachThingToCore(thingOne);
        iotAuthClientFake.activateCert(clientPem);

        runNucleusWithConfig("config.yaml");
        connectToCore(thingOne.get(), clientPem);

        // When
        corruptStoredClientCertificate(clientPem);

        // Then

        // Assert cert pem is corrupted
        ClientCertificateStore pemStore = kernel.getContext().get(ClientCertificateStore.class);
        String storeCertificatePem = pemStore.getPem(certificate.getCertificateId()).get();
        assertNotEquals(clientPem, storeCertificatePem);
        assertThrows(InvalidCertificateException.class, () -> Certificate.fromPem(storeCertificatePem));

        // Assert that authenticating offline or online is not affected
        network.goOffline();
        assertNotNull(connectToCore(thingOne.get(), clientPem));
        network.goOnline();
        assertNotNull(connectToCore(thingOne.get(), clientPem));
    }
}