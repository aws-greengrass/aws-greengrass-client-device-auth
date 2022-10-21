/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.infra.BackgroundCertificateRefresh;
import com.aws.greengrass.clientdevices.auth.certificate.infra.ClientCertificateStore;
import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.infra.NetworkState;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.clientdevices.auth.iot.usecases.VerifyIotCertificate;
import com.aws.greengrass.clientdevices.auth.iot.usecases.VerifyThingAttachedToCertificate;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;

import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ScopedMock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers.createClientCertificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class BackgroundCertificateRefreshTest {
    @Mock
    private GreengrassServiceClientFactory clientFactory;
    @Mock
    private GreengrassV2DataClient client;
    @Mock
    private NetworkState networkStateMock;
    @Mock
    private ScheduledThreadPoolExecutor schedulerMock;
    @Mock
    private VerifyThingAttachedToCertificate verifyThingAttachedToCertificateMock;
    @Mock
    private VerifyIotCertificate verifyIotCertificateMock;
    private IotAuthClientFake iotAuthClientFake;
    private CertificateRegistry certificateRegistry;
    private Topics configurationTopics;
    private ThingRegistry thingRegistry;
    private ClientCertificateStore pemStore;
    private BackgroundCertificateRefresh backgroundRefresh;
    private Optional<MockedStatic<Clock>> clockMock;


    @BeforeEach
    void setup() throws DeviceConfigurationException, KeyStoreException {
        iotAuthClientFake = spy(new IotAuthClientFake());
        lenient().when(clientFactory.fetchGreengrassV2DataClient()).thenReturn(client);

        DomainEvents domainEvents =  new DomainEvents();
        configurationTopics = Topics.of(new Context(), "config", null);
        RuntimeConfiguration runtimeConfiguration = RuntimeConfiguration.from(configurationTopics);
        pemStore = new ClientCertificateStore();
        certificateRegistry = new CertificateRegistry(runtimeConfiguration, pemStore);
        thingRegistry = new ThingRegistry(domainEvents, runtimeConfiguration);

        UseCases useCases = new UseCases();
        configurationTopics.getContext().put(VerifyThingAttachedToCertificate.class, verifyThingAttachedToCertificateMock);
        configurationTopics.getContext().put(VerifyIotCertificate.class, verifyIotCertificateMock);
        useCases.init(configurationTopics.getContext());

        this.clockMock = Optional.empty();

        backgroundRefresh = spy(new BackgroundCertificateRefresh(
                schedulerMock, thingRegistry, networkStateMock, 
                certificateRegistry, pemStore, iotAuthClientFake, useCases));
    }

    @AfterEach
    void cleanup() throws IOException {
        this.clockMock.ifPresent(ScopedMock::close);
        configurationTopics.getContext().close();
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

    private List<X509Certificate> generateClientCerts(int numberOfCerts) throws NoSuchAlgorithmException,
            CertificateException, OperatorCreationException, CertIOException {
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);
        ArrayList<X509Certificate> clientCerts = new ArrayList<>();

        for (int i = 0; i < numberOfCerts; i++) {
            KeyPair clientAKeyPair = CertificateStore.newRSAKeyPair(2048);
            clientCerts.add(createClientCertificate(
                    rootCA, "AWS IoT Certificate", clientAKeyPair.getPublic(), rootKeyPair.getPrivate()));
        }

        return clientCerts;
    }

    @Test
    void GIVEN_certsAndThings_WHEN_orphanedCertificate_THEN_itGetsRemoved() throws
            NoSuchAlgorithmException, CertificateException, OperatorCreationException, IOException,
            InvalidCertificateException {
        // Given
        Thing thingOne = Thing.of("ThingOne");
        Thing thingTwo = Thing.of("ThingTwo");
        List<X509Certificate> clientCerts = generateClientCerts(2);

        String certificateAPem = CertificateHelper.toPem(clientCerts.get(0));
        Certificate certA = Certificate.fromPem(certificateAPem);
        certificateRegistry.getOrCreateCertificate(certificateAPem);
        String certificateBPem = CertificateHelper.toPem(clientCerts.get(1));
        Certificate certB = Certificate.fromPem(certificateBPem);
        certificateRegistry.getOrCreateCertificate(certificateBPem);

        thingRegistry.createThing(thingOne.getThingName());
        thingRegistry.createThing(thingTwo.getThingName());

        // Attach the certificate to only one thing so that certificateBPem is orphaned
        thingOne.attachCertificate(certA.getCertificateId());
        thingRegistry.updateThing(thingOne);

        iotAuthClientFake.attachThingToCore(thingOne.getThingName());

        // When
        backgroundRefresh.run();

        // Then
        assertNotNull(thingRegistry.getThing(thingOne.getThingName()));
        assertNull(thingRegistry.getThing(thingTwo.getThingName()));
        assertTrue(certificateRegistry.getCertificateFromPem(certificateAPem).isPresent());
        assertEquals(pemStore.getPem(certA.getCertificateId()).get(), certificateAPem);
        assertFalse(certificateRegistry.getCertificateFromPem(certificateBPem).isPresent());
        assertFalse(pemStore.getPem(certB.getCertificateId()).isPresent());
    }

    @Test
    void GIVEN_certsAndThings_WHEN_certificateAssociatedToMoreThanOneThing_THEN_itDoesNotGetRemoved() throws
        Exception{
        // Given
        Thing thingOne = Thing.of("ThingOne");
        Thing thingTwo = Thing.of("ThingTwo");
        List<X509Certificate> clientCerts = generateClientCerts(1);

        String certificateAPem = CertificateHelper.toPem(clientCerts.get(0));
        Certificate certA = Certificate.fromPem(certificateAPem);
        certificateRegistry.getOrCreateCertificate(certificateAPem);

        thingRegistry.createThing(thingOne.getThingName());
        thingRegistry.createThing(thingTwo.getThingName());

        // Attach the certificate to only one thing so that certificateBPem is orphaned
        thingOne.attachCertificate(certA.getCertificateId());
        thingRegistry.updateThing(thingOne);
        thingTwo.attachCertificate(certA.getCertificateId());
        thingRegistry.updateThing(thingTwo);

        iotAuthClientFake.attachThingToCore(thingOne.getThingName());

        // When
        backgroundRefresh.run();

        // Then
        assertNotNull(thingRegistry.getThing(thingOne.getThingName()));
        assertNull(thingRegistry.getThing(thingTwo.getThingName()));
        assertTrue(certificateRegistry.getCertificateFromPem(certificateAPem).isPresent());
        assertEquals(pemStore.getPem(certA.getCertificateId()).get(), certificateAPem);
    }

    @Test
    void GIVEN_networkWasDown_WHEN_networkUp_THEN_backgroundTaskTriggered() {
        backgroundRefresh.accept(NetworkState.ConnectionState.NETWORK_DOWN);
        verify(backgroundRefresh, times(0)).run();
        backgroundRefresh.accept(NetworkState.ConnectionState.NETWORK_UP);
        verify(backgroundRefresh, times(1)).run();
        backgroundRefresh.accept(NetworkState.ConnectionState.NETWORK_DOWN);
        verify(backgroundRefresh, times(1)).run();
    }

    @Test
    void GIVEN_networkChangesAndScheduledByTime_WHEN_triggered_THEN_shouldNotRunMoreThanOnceADay() {
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());

        verify(iotAuthClientFake, times(0)).getThingsAssociatedWithCoreDevice();
        backgroundRefresh.run();
        backgroundRefresh.run();
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();

        Instant twentyFiveHoursLater = Instant.now().plus(Duration.ofHours(25));
        mockInstant(twentyFiveHoursLater.toEpochMilli());

        backgroundRefresh.run();
        verify(iotAuthClientFake, times(2)).getThingsAssociatedWithCoreDevice();
    }

    @Test
    void GIVEN_triggeredByMultipleThreads_WHEN_called_THEN_itShouldOnlyRunOnceForThatTimeWindow()
            throws InterruptedException {
       Thread t1 = new Thread(() -> { backgroundRefresh.run(); });
       Thread t2 = new Thread(() -> { backgroundRefresh.run(); });
       t1.start();
       t2.start();
       t1.join();
       t2.join();

        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();
    }

    @Test
    void GIVEN_triggeredInDifferentInstants_WHEN_triggeredExactlyOneDayLater_THEN_itShouldRunAgain() {
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());

        backgroundRefresh.run();

        Instant aDayLater = now.plus(Duration.ofHours(24));
        mockInstant(aDayLater.toEpochMilli());

        backgroundRefresh.run();
        verify(iotAuthClientFake, times(2)).getThingsAssociatedWithCoreDevice();
    }

    @Test
    void GIVEN_scheduler_WHEN_triggeredOnceADay_THEN_itShouldRunAtLeastOnceEveryDay() {
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());

        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();

        Instant alMostADayLater = now.plus(Duration.ofHours(24)).minus(Duration.ofSeconds(10));
        mockInstant(alMostADayLater.toEpochMilli());

        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();

        Instant aFullDayLater = now.plus(Duration.ofHours(24));
        mockInstant(aFullDayLater.toEpochMilli());

        backgroundRefresh.run();
        verify(iotAuthClientFake, times(2)).getThingsAssociatedWithCoreDevice();
    }
}