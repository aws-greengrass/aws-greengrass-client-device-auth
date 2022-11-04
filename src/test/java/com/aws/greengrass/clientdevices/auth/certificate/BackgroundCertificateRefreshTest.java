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
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.clientdevices.auth.iot.NetworkStateFake;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.dto.VerifyThingAttachedToCertificateDTO;
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
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ScopedMock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.model.AccessDeniedException;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
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
import java.util.function.Supplier;

import static com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers.createClientCertificate;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
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
    private ScheduledThreadPoolExecutor schedulerMock;
    @Mock
    private VerifyThingAttachedToCertificate verifyThingAttachedToCertificateMock;
    @Mock
    private VerifyIotCertificate verifyIotCertificateMock;
    private NetworkStateFake network;
    private IotAuthClientFake iotAuthClientFake;
    private CertificateRegistry certificateRegistry;
    private Topics configurationTopics;
    private ThingRegistry thingRegistry;
    private ClientCertificateStore pemStore;
    private BackgroundCertificateRefresh backgroundRefresh;
    private Optional<MockedStatic<Clock>> clockMock;
    @TempDir
    Path workDir;


    @BeforeEach
    void setup() throws DeviceConfigurationException {
        iotAuthClientFake = spy(new IotAuthClientFake());
        lenient().when(clientFactory.fetchGreengrassV2DataClient()).thenReturn(client);

        DomainEvents domainEvents = new DomainEvents();
        configurationTopics = Topics.of(new Context(), "config", null);
        RuntimeConfiguration runtimeConfiguration = RuntimeConfiguration.from(configurationTopics);
        pemStore = new ClientCertificateStore(workDir);
        certificateRegistry = new CertificateRegistry(runtimeConfiguration, pemStore);
        thingRegistry = new ThingRegistry(domainEvents, runtimeConfiguration);

        UseCases useCases = new UseCases();
        configurationTopics.getContext()
                .put(VerifyThingAttachedToCertificate.class, verifyThingAttachedToCertificateMock);
        configurationTopics.getContext().put(VerifyIotCertificate.class, verifyIotCertificateMock);
        useCases.init(configurationTopics.getContext());

        this.clockMock = Optional.empty();

        network = new NetworkStateFake();

        backgroundRefresh = spy(new BackgroundCertificateRefresh(schedulerMock, thingRegistry, network,
                certificateRegistry, pemStore, iotAuthClientFake, useCases));

        network.goOnline();
    }

    @AfterEach
    void cleanup() throws IOException {
        this.clockMock.ifPresent(ScopedMock::close);
        configurationTopics.getContext().close();
    }

    @SuppressWarnings("PMD.CloseResource")
    private void mockInstant(long expected) {
        this.clockMock.ifPresent(ScopedMock::close);
        MockedStatic<Clock> clockMockInternal = mockInstantForThread(expected);
        this.clockMock = Optional.of(clockMockInternal);
    }

    @SuppressWarnings("PMD.CloseResource")
    private MockedStatic<Clock> mockInstantForThread(long expected) {
        Clock spyClock = spy(Clock.class);
        MockedStatic<Clock> clockMock;
        clockMock = mockStatic(Clock.class);
        clockMock.when(Clock::systemUTC).thenReturn(spyClock);
        when(spyClock.instant()).thenReturn(Instant.ofEpochMilli(expected));
        return clockMock;
    }

    private List<X509Certificate> generateClientCerts(int numberOfCerts)
            throws NoSuchAlgorithmException, CertificateException, OperatorCreationException, CertIOException {
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
    void GIVEN_certsAndThings_WHEN_orphanedCertificate_THEN_itGetsRemoved() throws Exception {
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();

        // Given
        Supplier<String> thingOneName = () -> "ThingOne";
        Thing thingOne = Thing.of(thingOneName.get());
        Supplier<String> thingTwoName = () -> "ThingTwo";
        Thing thingTwo = Thing.of(thingTwoName.get());
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

        iotAuthClientFake.attachThingToCore(thingOneName);

        // When
        mockInstant(now.plus(Duration.ofHours(24)).toEpochMilli());
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
        Exception {
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();

        // Given
        Supplier<String> thingOneName = () -> "ThingOne";
        Thing thingOne = Thing.of(thingOneName.get());
        Supplier<String> thingTwoName = () -> "ThingTwo";
        Thing thingTwo = Thing.of(thingTwoName.get());
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

        iotAuthClientFake.attachThingToCore(thingOneName);

        // When
        mockInstant(now.plus(Duration.ofHours(24)).toEpochMilli());
        backgroundRefresh.run();

        // Then
        assertNotNull(thingRegistry.getThing(thingOne.getThingName()));
        assertNull(thingRegistry.getThing(thingTwo.getThingName()));
        assertTrue(certificateRegistry.getCertificateFromPem(certificateAPem).isPresent());
        assertEquals(pemStore.getPem(certA.getCertificateId()).get(), certificateAPem);
    }

    @Test
    void GIVEN_networkGoesOfflineAndScheduleTriggered_WHEN_networkComesUp_THEN_refreshTriggered() {
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();
        network.registerHandler(backgroundRefresh);

        network.goOffline();
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(0)).getThingsAssociatedWithCoreDevice();

        Instant justAfterOneDay = now.plus(Duration.ofHours(24)).plus(Duration.ofMillis(1));
        mockInstant(justAfterOneDay.toEpochMilli());

        network.goOnline();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();
    }

    @Test
    void GIVEN_deviceOnline_WHEN_iotCoreCallFails_THEN_itShouldStillScheduleNextRun(ExtensionContext context) {
        ignoreExceptionOfType(context, AccessDeniedException.class);
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();

        AccessDeniedException accessDeniedException = AccessDeniedException.builder().build();
        when(iotAuthClientFake.getThingsAssociatedWithCoreDevice()).thenThrow(accessDeniedException);

        mockInstant(now.plus(Duration.ofHours(24)).toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();

        assertEquals(backgroundRefresh.getNextScheduledRunInstant(), now.plus(Duration.ofHours(48)));
    }

    @Test
    void GIVEN_scheduleEver24Hours_WHEN_triggered_THEN_shouldNotRunMoreThanOnceEvery24Hours() {
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();

        Instant twoHoursLater = now.plus(Duration.ofHours(2));
        mockInstant(twoHoursLater.toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(0)).getThingsAssociatedWithCoreDevice();

        Instant aDayLater = now.plus(Duration.ofHours(24));
        mockInstant(aDayLater.toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();

        Instant justAfterADay = aDayLater.plus(Duration.ofNanos(1));
        mockInstant(justAfterADay.toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();

        Instant justBeforeTwoDays = aDayLater.plus(Duration.ofHours(24)).minus(Duration.ofNanos(1));
        mockInstant(justBeforeTwoDays.toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();

        Instant twoDaysLater = aDayLater.plus(Duration.ofHours(24));
        mockInstant(twoDaysLater.toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(2)).getThingsAssociatedWithCoreDevice();
    }

    @Test
    void GIVEN_triggeredByMultipleThreads_WHEN_called_THEN_itShouldOnlyRunOnceForThatTimeWindow()
            throws InterruptedException {
        Instant now = Instant.now();
         mockInstant(now.toEpochMilli());
        backgroundRefresh.start();

        Instant aDayLater = now.plus(Duration.ofHours(24));

        Thread t1 = new Thread(() -> {
            MockedStatic<Clock> clockT1 = mockInstantForThread(aDayLater.toEpochMilli());
            backgroundRefresh.run();
            clockT1.close();
        });
        Thread t2 = new Thread(() -> {
            MockedStatic<Clock> clockT2 = mockInstantForThread(aDayLater.toEpochMilli());
            backgroundRefresh.run();
            clockT2.close();
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();
    }

    @Test
    void GIVEN_cloudFailure_WHEN_verifyingAThingCertificateAttachment_THEN_refreshTaskShouldStillRun(
            ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, CloudServiceInteractionException.class);
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();

        // Given
        Supplier<String> thingOneName = () -> "ThingOne";
        Thing thingOne = Thing.of(thingOneName.get());
        Supplier<String> thingTwoName = () -> "ThingTwo";
        Thing thingTwo = Thing.of(thingTwoName.get());
        List<X509Certificate> clientCerts = generateClientCerts(2);

        String certificateAPem = CertificateHelper.toPem(clientCerts.get(0));
        Certificate certA = Certificate.fromPem(certificateAPem);
        certificateRegistry.getOrCreateCertificate(certificateAPem);
        String certificateBPem = CertificateHelper.toPem(clientCerts.get(1));
        Certificate certB = Certificate.fromPem(certificateBPem);
        certificateRegistry.getOrCreateCertificate(certificateBPem);

        thingRegistry.createThing(thingOne.getThingName());
        thingRegistry.createThing(thingTwo.getThingName());

        thingOne.attachCertificate(certA.getCertificateId());
        thingRegistry.updateThing(thingOne);
        thingTwo.attachCertificate(certB.getCertificateId());
        thingRegistry.updateThing(thingTwo);

        iotAuthClientFake.attachThingToCore(thingOneName);
        iotAuthClientFake.attachThingToCore(thingTwoName);


        Instant aDayLater = now.plus(Duration.ofHours(24));
        mockInstant(aDayLater.toEpochMilli());

        // When
        // Fail when verifying thingOne attachment to certA
        ArgumentCaptor<VerifyThingAttachedToCertificateDTO> doCaptor =
                ArgumentCaptor.forClass(VerifyThingAttachedToCertificateDTO.class);

        when(verifyThingAttachedToCertificateMock.apply(doCaptor.capture()))
                .thenThrow(new CloudServiceInteractionException("Failed to verify association"))
                .thenReturn(true);
        backgroundRefresh.run();

        // Then
        assertEquals(backgroundRefresh.getNextScheduledRunInstant(), aDayLater.plus(Duration.ofHours(24)));
    }

    @Test
    void GIVEN_scheduler_WHEN_runWithConnectivityIssues_THEN_itShouldRunEvery24H() {
        network.registerHandler(backgroundRefresh);
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();
        verify(backgroundRefresh, times(0)).run();

        // Network goes down and back up some time later
        network.goOffline();
        Instant twelveHoursLater = now.plus(Duration.ofHours(12));
        mockInstant(twelveHoursLater.toEpochMilli());
        network.goOnline();
        verify(iotAuthClientFake, times(0)).getThingsAssociatedWithCoreDevice();

        // Something calls the task directly 12 hours after the last ran
        Instant aDayLater = twelveHoursLater.plus(Duration.ofHours(24));
        mockInstant(aDayLater.toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();
    }

    @Test
    void GIVEN_scheduler_WHEN_runWithNetworkFailures_THEN_itShouldRunEvery24H(ExtensionContext context) {
        ignoreExceptionOfType(context, AccessDeniedException.class);
        network.registerHandler(backgroundRefresh);
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();
        verify(backgroundRefresh, times(0)).run();

        // Time of next run fails
        AccessDeniedException err = AccessDeniedException.builder().build();
        when(iotAuthClientFake.getThingsAssociatedWithCoreDevice()).thenThrow(err);
        Instant aDayLater = now.plus(Duration.ofHours(24));
        mockInstant(aDayLater.toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();

        // Network goes down and back up again, but this time the error goes away
        network.goOffline();
        Instant twelveHoursAfterExecution = aDayLater.plus(Duration.ofHours(12));
        mockInstant(twelveHoursAfterExecution.toEpochMilli());
        reset(iotAuthClientFake); // <- We are removing the error here
        network.goOnline();
        verify(iotAuthClientFake, times(0)).getThingsAssociatedWithCoreDevice();
    }
}