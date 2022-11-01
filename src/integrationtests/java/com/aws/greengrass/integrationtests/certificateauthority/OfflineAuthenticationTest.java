/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.certificateauthority;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.infra.ClientCertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.infra.BackgroundCertificateRefresh;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.clientdevices.auth.iot.NetworkStateFake;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.ScopedMock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers.createClientCertificate;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class OfflineAuthenticationTest {
    @TempDir
    Path rootDir;
    private Kernel kernel;
    private IotAuthClientFake iotAuthClientFake;
    private Optional<MockedStatic<Clock>> clockMock;
    private NetworkStateFake network;


    @BeforeEach
    void setup(ExtensionContext context) {
        ignoreExceptionOfType(context, SpoolerStoreException.class);

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();
        network = new NetworkStateFake();
        kernel.getContext().put(NetworkStateProvider.class, network);

        DomainEvents domainEvents =  new DomainEvents();
        kernel.getContext().put(DomainEvents.class, domainEvents);

        iotAuthClientFake = new IotAuthClientFake();
        kernel.getContext().put(IotAuthClient.class, iotAuthClientFake);

        clockMock = Optional.empty();
    }

    @AfterEach
    void cleanup() {
        this.clockMock.ifPresent(ScopedMock::close);
        kernel.shutdown();
    }

    @SuppressWarnings("PMD.CloseResource")
    private void mockInstant(long expected) {
        this.clockMock.ifPresent(ScopedMock::close);
        Clock spyClock = spy(Clock.class);
        MockedStatic<Clock> clockMock;
        clockMock = mockStatic(Clock.class);
        clockMock.when(Clock::systemUTC).thenReturn(spyClock);
        when(spyClock.instant()).thenReturn(Instant.ofEpochMilli(expected));
        this.clockMock = Optional.of(clockMock);
    }

    private void runNucleusWithConfig(String configFileName) throws InterruptedException {
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

    private List<X509Certificate> createClientCertificates(int amount) throws Exception {
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);

        List<X509Certificate> clientCertificates = new ArrayList<>();

        for (int i = 0; i < amount; i++) {
            KeyPair clientKeyPair = CertificateStore.newRSAKeyPair(2048);

            clientCertificates.add(createClientCertificate(
                    rootCA, "AWS IoT Certificate", clientKeyPair.getPublic(), rootKeyPair.getPrivate()));
        }

        return clientCertificates;
    }

    private String connectToCore(String thingName, String certificatePem) throws AuthenticationException {
        ClientDevicesAuthServiceApi api = kernel.getContext().get(ClientDevicesAuthServiceApi.class);
        // Simulate some client components (like Moquette) verifying some certificates
        api.verifyClientDeviceIdentity(certificatePem);
        // Simulate a client connecting and generating a session
        return api.getClientDeviceAuthToken("mqtt",   new HashMap<String, String>() {{
            put("clientId", thingName);
            put("certificatePem", certificatePem);
            put("username", "foo");
            put("password", "bar");
        }});
    }

    @Test
    void GIVEN_clientDevice_WHEN_verifyingItsIdentity_THEN_pemStored(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, NoSuchFileException.class);
        // Given
        List<X509Certificate> clientCertificates = createClientCertificates(1);

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
   void GIVEN_storedCertificates_WHEN_refreshEnabled_THEN_storedCertificatesRefreshed(ExtensionContext context)
           throws Exception {
        ignoreExceptionOfType(context, NoSuchFileException.class);
       // Given
       List<X509Certificate> clientCertificates = createClientCertificates(2);

       // Configure the IotClientFake
       String clientAPem = CertificateHelper.toPem(clientCertificates.get(0));
       iotAuthClientFake.activateCert(clientAPem);
       String clientBPem = CertificateHelper.toPem(clientCertificates.get(1));
       iotAuthClientFake.activateCert(clientBPem);
       Supplier<String> thingOne =  () -> "ThingOne";
       iotAuthClientFake.attachCertificateToThing(thingOne.get(), clientAPem);
       Supplier<String> thingTwo = () -> "ThingTwo";
       iotAuthClientFake.attachCertificateToThing(thingTwo.get(), clientBPem);
       iotAuthClientFake.attachThingToCore(thingOne);
       iotAuthClientFake.attachThingToCore(thingTwo);

       network.goOnline();
       runNucleusWithConfig("config.yaml");

       Instant now = Instant.now();
       mockInstant(now.toEpochMilli());

       connectToCore(thingOne.get(), clientAPem);
       connectToCore(thingTwo.get(), clientBPem);

       // Check state before refresh of the certificates
       CertificateRegistry certRegistry = kernel.getContext().get(CertificateRegistry.class);
       Certificate ogCertA = certRegistry.getCertificateFromPem(clientAPem).get();
       Certificate ogCertB = certRegistry.getCertificateFromPem(clientBPem).get();
       assertEquals(ogCertA.getStatusLastUpdated().toEpochMilli(), now.toEpochMilli());
       assertEquals(ogCertB.getStatusLastUpdated().toEpochMilli(), now.toEpochMilli());

       // Check state before refresh of thing attachments
       ThingRegistry thingRegistry = kernel.getContext().get(ThingRegistry.class);
       Thing ogThingA = thingRegistry.getOrCreateThing(thingOne.get());
       Thing ogThingB = thingRegistry.getOrCreateThing(thingTwo.get());
       assertEquals(ogThingA.certificateLastAttachedOn(ogCertA.getCertificateId()).get().toEpochMilli(), now.toEpochMilli());
       assertEquals(ogThingB.certificateLastAttachedOn(ogCertB.getCertificateId()).get().toEpochMilli(), now.toEpochMilli());

       // Detach one thing from the core
       iotAuthClientFake.detachThingFromCore(thingTwo);

       // When
       Instant anHourLater = now.plusSeconds(60 * 60);
       mockInstant(anHourLater.toEpochMilli());

       BackgroundCertificateRefresh backgroundRefresh = kernel.getContext().get(BackgroundCertificateRefresh.class);
       assertTrue(backgroundRefresh.isRunning(), "background refresh is not running");
       backgroundRefresh.run(); // Force a run because otherwise it is controlled by a ScheduledExecutorService
       kernel.getConfig().waitConfigUpdateComplete();

       // Then

       // Verify certificates updated after refresh
       Optional<Certificate> certA = certRegistry.getCertificateFromPem(clientAPem);
       Optional<Certificate> certB = certRegistry.getCertificateFromPem(clientBPem);
       assertEquals(certA.get().getStatusLastUpdated().toEpochMilli(), anHourLater.toEpochMilli());
       // Given certB was only attached to thingB and thingB got detached it is deleted from the registry.
       assertFalse(certB.isPresent());

       // Verify thing certificate attachments got updated after refresh
       Thing thingA = thingRegistry.getThing(thingOne.get());
       Thing thingB = thingRegistry.getThing(thingTwo.get());
       assertEquals(
           thingA.certificateLastAttachedOn(ogCertA.getCertificateId()).get().toEpochMilli(),
           anHourLater.toEpochMilli()
       );
       // This one should have been removed given it is no longer attached
       assertNull(thingB);
   }

   @Test
   void GIVEN_clientConnectsWhileOnline_WHEN_offline_THEN_clientCanConnect(ExtensionContext context) throws Exception {
       ignoreExceptionOfType(context, NoSuchFileException.class);

       // Given
       network.goOnline();

       // Configure the things attached to the core
       List<X509Certificate> clientCertificates = createClientCertificates(1);
       String clientPem = CertificateHelper.toPem(clientCertificates.get(0));
       Supplier<String> thingOne =  () -> "ThingOne";
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
       mockInstant(now.toEpochMilli());

       // Configure the things attached to the core
       List<X509Certificate> clientCertificates = createClientCertificates(1);
       String clientPem = CertificateHelper.toPem(clientCertificates.get(0));
       Supplier<String> thingOne =  () -> "ThingOne";
       iotAuthClientFake.attachCertificateToThing(thingOne.get(), clientPem);
       iotAuthClientFake.attachThingToCore(thingOne);
       iotAuthClientFake.activateCert(clientPem);

       runNucleusWithConfig("config.yaml");
       assertNotNull(connectToCore(thingOne.get(), clientPem));

       // When
       network.goOffline();
       Instant twoMinutesLater = now.plusSeconds(2 * 60); // Default expiry is 1 minute
       mockInstant(twoMinutesLater.toEpochMilli());

       // Then
       assertThrows(AuthenticationException.class, () -> {connectToCore(thingOne.get(), clientPem);});
   }

   @Test
    void GIVEN_clientConnectsWhileOnline_WHEN_offlineAndCertificateRevoked_THEN_backOnlineAndClientRejected(
            ExtensionContext context) throws Exception {
       ignoreExceptionOfType(context, NoSuchFileException.class);
       // Given
       network.goOnline();

       // Configure the things attached to the core
       List<X509Certificate> clientCertificates = createClientCertificates(1);
       String clientPem = CertificateHelper.toPem(clientCertificates.get(0));
       Supplier<String> thingOne =  () -> "ThingOne";
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
       assertThrows(AuthenticationException.class, () -> {connectToCore(thingOne.get(), clientPem);});
   }

    @Test
    void GIVEN_clientConnectsWhileOnline_WHEN_offlineAndCertDetachedFromThing_THEN_backOnlineAndClientRejected(
            ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, NoSuchFileException.class);
        // Given
        network.goOnline();

        // Configure the things attached to the core
        List<X509Certificate> clientCertificates = createClientCertificates(1);
        String clientPem = CertificateHelper.toPem(clientCertificates.get(0));
        Supplier<String> thingOne =  () -> "ThingOne";
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
        assertThrows(AuthenticationException.class, () -> {connectToCore(thingOne.get(), clientPem);});
    }
}