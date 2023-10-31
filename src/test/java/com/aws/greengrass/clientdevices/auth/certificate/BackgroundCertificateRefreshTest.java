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
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
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
import org.junit.jupiter.api.Disabled;
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
    private NetworkStateProvider.Default networkStateMock;
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

        backgroundRefresh = spy(new BackgroundCertificateRefresh(schedulerMock, thingRegistry, networkStateMock,
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

    private List<X509Certificate> generateClientCerts(int numberOfCerts)
            throws NoSuchAlgorithmException, CertificateException, OperatorCreationException, CertIOException {
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);
        ArrayList<X509Certificate> clientCerts = new ArrayList<>();

        for (int i = 0; i < numberOfCerts; i++) {
            KeyPair clientAKeyPair = CertificateStore.newRSAKeyPair(2048);
            clientCerts.add(createClientCertificate(rootCA, "AWS IoT Certificate", clientAKeyPair.getPublic(),
                    rootKeyPair.getPrivate()));
        }

        return clientCerts;
    }

    @Test
    void GIVEN_certsAndThings_WHEN_orphanedCertificate_THEN_itGetsRemoved()
            throws NoSuchAlgorithmException, CertificateException, OperatorCreationException, IOException,
            InvalidCertificateException {
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
    void GIVEN_certsAndThings_WHEN_certificateAssociatedToMoreThanOneThing_THEN_itDoesNotGetRemoved() throws Exception {
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
    void GIVEN_networkWasDown_WHEN_networkUp_THEN_backgroundTaskTriggered() {
        backgroundRefresh.accept(NetworkStateProvider.ConnectionState.NETWORK_DOWN);
        verify(backgroundRefresh, times(0)).run();
        backgroundRefresh.accept(NetworkStateProvider.ConnectionState.NETWORK_UP);
        verify(backgroundRefresh, times(1)).run();
        backgroundRefresh.accept(NetworkStateProvider.ConnectionState.NETWORK_DOWN);
        verify(backgroundRefresh, times(1)).run();
    }

    @Test
    void GIVEN_deviceOnline_WHEN_iotCoreCallFails_THEN_itShouldStillGetCalled24HAfterFailure(
            ExtensionContext context) {
        ignoreExceptionOfType(context, AccessDeniedException.class);
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();

        AccessDeniedException accessDeniedException = AccessDeniedException.builder().build();
        when(iotAuthClientFake.getThingsAssociatedWithCoreDevice()).thenThrow(accessDeniedException);

        Instant aDayLater = now.plus(Duration.ofHours(24));
        mockInstant(aDayLater.toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();

        assertEquals(backgroundRefresh.getNextScheduledRun(), aDayLater.plus(Duration.ofHours(24)));
        mockInstant(aDayLater.plus(Duration.ofHours(24)).toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(2)).getThingsAssociatedWithCoreDevice();
    }

    @Test
    void GIVEN_networkChangesAndScheduledByTime_WHEN_triggered_THEN_shouldNotRunMoreThanOnceADay() {
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();

        verify(iotAuthClientFake, times(0)).getThingsAssociatedWithCoreDevice();
        backgroundRefresh.run();
        backgroundRefresh.run();
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(0)).getThingsAssociatedWithCoreDevice();

        Instant twentyFiveHoursLater = Instant.now().plus(Duration.ofHours(25));
        mockInstant(twentyFiveHoursLater.toEpochMilli());

        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();
    }

    @Test
    @Disabled("For this test to pass we need to fix how we mock time")
    void GIVEN_triggeredByMultipleThreads_WHEN_called_THEN_itShouldOnlyRunOnceForThatTimeWindow()
            throws InterruptedException {
        Thread t1 = new Thread(() -> {
            backgroundRefresh.run();
        });
        Thread t2 = new Thread(() -> {
            backgroundRefresh.run();
        });
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
        backgroundRefresh.start();

        Instant twoHoursLater = now.plus(Duration.ofHours(2));
        mockInstant(twoHoursLater.toEpochMilli());

        backgroundRefresh.run();

        Instant aDayLater = now.plus(Duration.ofHours(24));
        mockInstant(aDayLater.toEpochMilli());

        backgroundRefresh.run();
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

        // When
        // Fail when verifying thingOne attachment to certA
        ArgumentCaptor<VerifyThingAttachedToCertificateDTO> doCaptor =
                ArgumentCaptor.forClass(VerifyThingAttachedToCertificateDTO.class);

        when(verifyThingAttachedToCertificateMock.apply(doCaptor.capture())).thenReturn(
                VerifyThingAttachedToCertificate.Result.builder().thingHasValidAttachmentToCertificate(false).build()).thenReturn(
                        VerifyThingAttachedToCertificate.Result.builder().thingHasValidAttachmentToCertificate(true).build());
        Instant twentyFourHoursLater = now.plus(Duration.ofHours(24));
        mockInstant(twentyFourHoursLater.toEpochMilli());
        assertNull(backgroundRefresh.getLastRan());
        backgroundRefresh.run();

        // Then
        assertEquals(backgroundRefresh.getNextScheduledRun(), twentyFourHoursLater.plus(Duration.ofHours(24)));
        assertEquals(twentyFourHoursLater, backgroundRefresh.getLastRan());
    }

    @Test
    void GIVEN_scheduler_WHEN_runWithConnectivityIssues_THEN_itShouldRunEvery24H() {
        // Service get started on boot-up
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();
        assertEquals(now.plus(Duration.ofHours(24)), backgroundRefresh.getNextScheduledRun());

        // Network goes down and back up some time later
        Instant twelveHoursLater =  now.plus(Duration.ofHours(12));
        mockInstant(twelveHoursLater.toEpochMilli());
        backgroundRefresh.accept(NetworkStateProvider.ConnectionState.NETWORK_UP);
        verify(iotAuthClientFake, times(0)).getThingsAssociatedWithCoreDevice();
        assertEquals(now.plus(Duration.ofHours(24)), backgroundRefresh.getNextScheduledRun());
        assertNull(backgroundRefresh.getLastRan());

        // Something calls the task directly 12 hours after the last ran
        Instant twentyFourHoursLater = twelveHoursLater.plus(Duration.ofHours(12));
        mockInstant(twentyFourHoursLater.toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();
        assertEquals(twentyFourHoursLater.plus(Duration.ofHours(24)), backgroundRefresh.getNextScheduledRun());
        assertEquals(twentyFourHoursLater, backgroundRefresh.getLastRan());

        // The scheduler runs
        Instant timeOfNextScheduledRun = backgroundRefresh.getNextScheduledRun();
        mockInstant(timeOfNextScheduledRun.toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(2)).getThingsAssociatedWithCoreDevice();
        assertEquals(timeOfNextScheduledRun.plus(Duration.ofHours(24)), backgroundRefresh.getNextScheduledRun());
        assertEquals(timeOfNextScheduledRun, backgroundRefresh.getLastRan());
    }

    @Test
    void GIVEN_scheduler_WHEN_runWithNetworkFailures_THEN_itShouldRunEvery24H(ExtensionContext context) {
        ignoreExceptionOfType(context, AccessDeniedException.class);
        // Service get started on boot-up
        Instant now = Instant.now();
        mockInstant(now.toEpochMilli());
        backgroundRefresh.start();
        assertEquals(now.plus(Duration.ofHours(24)), backgroundRefresh.getNextScheduledRun());
        verify(backgroundRefresh, times(0)).run();

        // Time of next run fails
        AccessDeniedException err = AccessDeniedException.builder().build();
        when(iotAuthClientFake.getThingsAssociatedWithCoreDevice()).thenThrow(err);
        Instant timeOfNextScheduledRun = backgroundRefresh.getNextScheduledRun();
        mockInstant(timeOfNextScheduledRun.toEpochMilli());
        backgroundRefresh.run();
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();
        assertEquals(timeOfNextScheduledRun.plus(Duration.ofHours(24)), backgroundRefresh.getNextScheduledRun());
        assertNull(backgroundRefresh.getLastRan());

        // Network goes down and back up again, but this time the error goes away
        Instant twentyFourHoursAfterExecution = timeOfNextScheduledRun.plus(Duration.ofHours(24));
        mockInstant(twentyFourHoursAfterExecution.toEpochMilli());
        reset(iotAuthClientFake); // <- We are removing the error here
        backgroundRefresh.accept(NetworkStateProvider.ConnectionState.NETWORK_UP);
        verify(iotAuthClientFake, times(1)).getThingsAssociatedWithCoreDevice();
        assertEquals(twentyFourHoursAfterExecution.plus(Duration.ofHours(24)), backgroundRefresh.getNextScheduledRun());
        assertEquals(twentyFourHoursAfterExecution, backgroundRefresh.getLastRan());
    }
}
