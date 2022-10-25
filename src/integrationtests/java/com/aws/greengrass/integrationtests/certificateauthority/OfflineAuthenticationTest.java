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
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ScopedMock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private Optional<MockedStatic<Clock>> clockMock;


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

        clockMock = Optional.empty();
        lenient().when(clientFactory.fetchGreengrassV2DataClient()).thenReturn(client);
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
    void GIVEN_clientDevice_WHEN_verifyingItsIdentity_THEN_pemStored() throws Exception {
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
   void GIVEN_storedCertificates_WHEN_refreshEnabled_THEN_storedCertificatesRefreshed() throws Exception {
       // Given

       // Generate some credentials for the fake client devices
       KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
       X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);
       KeyPair clientAKeyPair = CertificateStore.newRSAKeyPair(2048);
       X509Certificate clientACrt = createClientCertificate(
               rootCA, "AWS IoT Certificate", clientAKeyPair.getPublic(), rootKeyPair.getPrivate());
       KeyPair clientBKeyPair = CertificateStore.newRSAKeyPair(2048);
       X509Certificate clientBCrt = createClientCertificate(
               rootCA, "AWS IoT Certificate", clientBKeyPair.getPublic(), rootKeyPair.getPrivate());

       // Configure the IotClientFake
       String clientAPem = CertificateHelper.toPem(clientACrt);
       iotAuthClientFake.activateCert(clientAPem);
       String clientBPem = CertificateHelper.toPem(clientBCrt);
       iotAuthClientFake.activateCert(clientBPem);
       when(networkStateMock.getConnectionStateFromMqtt()).thenReturn(NetworkState.ConnectionState.NETWORK_UP);
       Supplier<String> thingOne =  () -> "ThingOne";
       iotAuthClientFake.attachCertificateToThing(thingOne.get(), clientAPem);
       Supplier<String> thingTwo = () -> "ThingTwo";
       iotAuthClientFake.attachCertificateToThing(thingTwo.get(), clientBPem);

       iotAuthClientFake.attachThingToCore(thingOne);
       iotAuthClientFake.attachThingToCore(thingTwo);

       givenNucleusRunningWithConfig("config.yaml");

       Instant now = Instant.now();
       mockInstant(now.toEpochMilli());
       ClientDevicesAuthServiceApi api = kernel.getContext().get(ClientDevicesAuthServiceApi.class);

       // Simulate some client components (like Moquette) verifying some certificates
       api.verifyClientDeviceIdentity(clientAPem);
       api.verifyClientDeviceIdentity(clientBPem);

       // Simulate a client connecting and generating a session
       api.getClientDeviceAuthToken("mqtt",   new HashMap<String, String>() {{
           put("clientId", thingOne.get());
           put("certificatePem", clientAPem);
           put("username", "foo");
           put("password", "bar");
       }});
       api.getClientDeviceAuthToken("mqtt", new HashMap<String, String>() {{
           put("clientId", thingTwo.get());
           put("certificatePem", clientBPem);
           put("username", "baz");
           put("password", "fizz");
       }});

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

}