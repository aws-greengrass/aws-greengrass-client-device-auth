/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;
import com.aws.greengrass.clientdevices.auth.connectivity.UpdateConnectivityInformationRequest;
import com.aws.greengrass.clientdevices.auth.connectivity.UpdateConnectivityInformationResponse;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;


@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
@ExtendWith({MockitoExtension.class, GGExtension.class})
public class ConnectivityInformationUseCasesTest {
    private Context context;
    private UseCases useCases;

    private static final String defaultSource = "source";
    private static final HostAddress supersetHost = HostAddress.of("127.0.0.2");
    private static final Set<HostAddress> sourceConnectivityInfo = Stream.of("localhost", "127.0.0.1")
            .map(HostAddress::of)
            .collect(Collectors.toSet());
    private static final Set<HostAddress> connectivityInfoSuperset = Stream.of("localhost", "127.0.0.1", "127.0.0.2")
            .map(HostAddress::of)
            .collect(Collectors.toSet());

    @BeforeEach
    public void setup() {
        context = new Context();

        // Note: these can be removed once ConnectivityInformation no longer requires these objects
        context.put(DeviceConfiguration.class, Mockito.mock(DeviceConfiguration.class));
        context.put(GreengrassServiceClientFactory.class, Mockito.mock(GreengrassServiceClientFactory.class));

        Topics topics = Topics.of(context, CONFIGURATION_CONFIG_KEY, null);
        useCases = new UseCases(topics);
    }

    @Test
    void GIVEN_emptyConnectivityInfo_WHEN_getConnectivityInformation_THEN_returnEmptySet() {
        GetConnectivityInformationUseCase useCase =
                (GetConnectivityInformationUseCase) useCases.get(GetConnectivityInformationUseCase.class);
        Set<HostAddress> connectivityInfo = useCase.execute(null);
        assertThat(connectivityInfo, is(empty()));
    }

    @Test
    void GIVEN_connectivityInfo_WHEN_updateUseCase_THEN_connectivityInfoIsAdded() {
        GetConnectivityInformationUseCase getUseCase =
                (GetConnectivityInformationUseCase) useCases.get(GetConnectivityInformationUseCase.class);
        UpdateConnectivityInformationUseCase updateUseCase =
                (UpdateConnectivityInformationUseCase) useCases.get(UpdateConnectivityInformationUseCase.class);

        UpdateConnectivityInformationRequest request =
                new UpdateConnectivityInformationRequest(defaultSource, sourceConnectivityInfo);
        UpdateConnectivityInformationResponse updateResponse = updateUseCase.execute(request);
        assertTrue(updateResponse.didChange());
        assertEquals(updateResponse.getAddedHostAddresses(), sourceConnectivityInfo);
        assertTrue(updateResponse.getRemovedHostAddresses().isEmpty());

        Set<HostAddress> retrievedConnectivityInfo = getUseCase.execute(null);
        assertEquals(retrievedConnectivityInfo, sourceConnectivityInfo);
    }

    @Test
    void GIVEN_duplicateConnectivityInfo_WHEN_updateUseCase_THEN_noChange() {
        UpdateConnectivityInformationUseCase updateUseCase =
                (UpdateConnectivityInformationUseCase) useCases.get(UpdateConnectivityInformationUseCase.class);

        UpdateConnectivityInformationRequest request =
                new UpdateConnectivityInformationRequest(defaultSource, sourceConnectivityInfo);

        UpdateConnectivityInformationResponse updateResponse = updateUseCase.execute(request);
        assertTrue(updateResponse.didChange()); // Do something with initial response to prevent PMD violation

        updateResponse = updateUseCase.execute(request);
        assertFalse(updateResponse.didChange());
        assertTrue(updateResponse.getAddedHostAddresses().isEmpty());
        assertTrue(updateResponse.getRemovedHostAddresses().isEmpty());
    }

    @Test
    void GIVEN_connectivityInformationSuperset_WHEN_updateUseCase_THEN_onlyConnectivityDeltaChanges() {
        UpdateConnectivityInformationUseCase updateUseCase =
                (UpdateConnectivityInformationUseCase) useCases.get(UpdateConnectivityInformationUseCase.class);

        UpdateConnectivityInformationRequest request =
                new UpdateConnectivityInformationRequest(defaultSource, sourceConnectivityInfo);

        UpdateConnectivityInformationResponse updateResponse = updateUseCase.execute(request);
        assertTrue(updateResponse.didChange()); // Do something with initial response to prevent PMD violation

        request = new UpdateConnectivityInformationRequest(defaultSource, connectivityInfoSuperset);

        updateResponse = updateUseCase.execute(request);
        assertTrue(updateResponse.didChange());
        assertEquals(updateResponse.getAddedHostAddresses(), new HashSet<>(Collections.singleton(supersetHost)));
        assertEquals(updateResponse.getRemovedHostAddresses(), Collections.emptySet());
    }

    @Test
    void GIVEN_connectivityInformationSubset_WHEN_updateUseCase_THEN_onlyConnectivityDeltaChanges() {
        UpdateConnectivityInformationUseCase updateUseCase =
                (UpdateConnectivityInformationUseCase) useCases.get(UpdateConnectivityInformationUseCase.class);

        UpdateConnectivityInformationRequest request =
                new UpdateConnectivityInformationRequest(defaultSource, connectivityInfoSuperset);

        UpdateConnectivityInformationResponse updateResponse = updateUseCase.execute(request);
        assertTrue(updateResponse.didChange()); // Do something with initial response to prevent PMD violation

        request = new UpdateConnectivityInformationRequest(defaultSource, sourceConnectivityInfo);

        updateResponse = updateUseCase.execute(request);
        assertTrue(updateResponse.didChange());
        assertEquals(updateResponse.getAddedHostAddresses(), Collections.emptySet());
        assertEquals(updateResponse.getRemovedHostAddresses(), new HashSet<>(Collections.singleton(supersetHost)));
    }

    @Test
    void GIVEN_secondSourceWithConnectivityInformationSubset_WHEN_updateUseCase_THEN_noChange() {
        UpdateConnectivityInformationUseCase updateUseCase =
                (UpdateConnectivityInformationUseCase) useCases.get(UpdateConnectivityInformationUseCase.class);

        UpdateConnectivityInformationRequest request =
                new UpdateConnectivityInformationRequest(defaultSource, connectivityInfoSuperset);

        UpdateConnectivityInformationResponse updateResponse = updateUseCase.execute(request);
        assertTrue(updateResponse.didChange()); // Do something with initial response to prevent PMD violation

        request = new UpdateConnectivityInformationRequest("source2", sourceConnectivityInfo);

        updateResponse = updateUseCase.execute(request);
        assertFalse(updateResponse.didChange());
        assertEquals(updateResponse.getAddedHostAddresses(), Collections.emptySet());
        assertEquals(updateResponse.getRemovedHostAddresses(), Collections.emptySet());
    }

    @Test
    void GIVEN_emptySetAfterUpdatingConnectivityInformation_WHEN_updateUseCase_THEN_connectivityInfoRemoved() {
        UpdateConnectivityInformationUseCase updateUseCase =
                (UpdateConnectivityInformationUseCase) useCases.get(UpdateConnectivityInformationUseCase.class);

        UpdateConnectivityInformationRequest request =
                new UpdateConnectivityInformationRequest(defaultSource, sourceConnectivityInfo);

        UpdateConnectivityInformationResponse updateResponse = updateUseCase.execute(request);
        assertTrue(updateResponse.didChange()); // Do something with initial response to prevent PMD violation

        request = new UpdateConnectivityInformationRequest(defaultSource, Collections.emptySet());

        updateResponse = updateUseCase.execute(request);
        assertTrue(updateResponse.didChange());
        assertEquals(updateResponse.getAddedHostAddresses(), Collections.emptySet());
        assertEquals(updateResponse.getRemovedHostAddresses(), sourceConnectivityInfo);
    }
}
