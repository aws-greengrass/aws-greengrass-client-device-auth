/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformationSource;
import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;
import com.aws.greengrass.clientdevices.auth.connectivity.RecordConnectivityChangesRequest;
import com.aws.greengrass.clientdevices.auth.connectivity.RecordConnectivityChangesResponse;
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

    private static final ConnectivityInformationSource defaultSource = ConnectivityInformationSource.CONNECTIVITY_INFORMATION_SERVICE;
    private static final HostAddress supersetHost = HostAddress.of("127.0.0.2");
    private static final Set<HostAddress> sourceConnectivityInfo =
            Stream.of("localhost", "127.0.0.1").map(HostAddress::of).collect(Collectors.toSet());
    private static final Set<HostAddress> connectivityInfoSuperset =
            Stream.of("localhost", "127.0.0.1", "127.0.0.2").map(HostAddress::of).collect(Collectors.toSet());
    private UseCases useCases;

    @BeforeEach
    public void setup() {
        context = new Context();

        // Note: these can be removed once ConnectivityInformation no longer requires these objects
        context.put(DeviceConfiguration.class, Mockito.mock(DeviceConfiguration.class));
        context.put(GreengrassServiceClientFactory.class, Mockito.mock(GreengrassServiceClientFactory.class));

        Topics topics = Topics.of(context, CONFIGURATION_CONFIG_KEY, null);
        this.useCases = new UseCases(topics.getContext());
        this.useCases.init(topics.getContext());
    }

    @Test
    void GIVEN_emptyConnectivityInfo_WHEN_getConnectivityInformation_THEN_returnEmptySet() {
        GetConnectivityInformationUseCase useCase = useCases.get(GetConnectivityInformationUseCase.class);
        Set<HostAddress> connectivityInfo = useCase.apply(null);
        assertThat(connectivityInfo, is(empty()));
    }

    @Test
    void GIVEN_connectivityInfo_WHEN_recordChangesUseCase_THEN_connectivityInfoIsAdded() {
        GetConnectivityInformationUseCase getUseCase = useCases.get(GetConnectivityInformationUseCase.class);
        RecordConnectivityChangesUseCase recordChangeUseCase = useCases.get(RecordConnectivityChangesUseCase.class);

        RecordConnectivityChangesRequest request =
                new RecordConnectivityChangesRequest(defaultSource, sourceConnectivityInfo);
        RecordConnectivityChangesResponse recordChangeResponse = recordChangeUseCase.apply(request);
        assertTrue(recordChangeResponse.didChange());
        assertEquals(recordChangeResponse.getAddedHostAddresses(), sourceConnectivityInfo);
        assertTrue(recordChangeResponse.getRemovedHostAddresses().isEmpty());

        Set<HostAddress> retrievedConnectivityInfo = getUseCase.apply(null);
        assertEquals(retrievedConnectivityInfo, sourceConnectivityInfo);
    }

    @Test
    void GIVEN_duplicateConnectivityInfo_WHEN_recordChangesUseCase_THEN_noChange() {
        RecordConnectivityChangesUseCase recordChangeUseCase = useCases.get(RecordConnectivityChangesUseCase.class);

        RecordConnectivityChangesRequest request =
                new RecordConnectivityChangesRequest(defaultSource, sourceConnectivityInfo);

        RecordConnectivityChangesResponse recordChangeResponse = recordChangeUseCase.apply(request);
        assertTrue(recordChangeResponse.didChange()); // Do something with initial response to prevent PMD violation

        recordChangeResponse = recordChangeUseCase.apply(request);
        assertFalse(recordChangeResponse.didChange());
        assertTrue(recordChangeResponse.getAddedHostAddresses().isEmpty());
        assertTrue(recordChangeResponse.getRemovedHostAddresses().isEmpty());
    }

    @Test
    void GIVEN_connectivityInformationSuperset_WHEN_recordChangesUseCase_THEN_onlyConnectivityDeltaChanges() {
        RecordConnectivityChangesUseCase recordChangeUseCase = useCases.get(RecordConnectivityChangesUseCase.class);

        RecordConnectivityChangesRequest request =
                new RecordConnectivityChangesRequest(defaultSource, sourceConnectivityInfo);

        RecordConnectivityChangesResponse recordChangeResponse = recordChangeUseCase.apply(request);
        assertTrue(recordChangeResponse.didChange()); // Do something with initial response to prevent PMD violation

        request = new RecordConnectivityChangesRequest(defaultSource, connectivityInfoSuperset);

        recordChangeResponse = recordChangeUseCase.apply(request);
        assertTrue(recordChangeResponse.didChange());
        assertEquals(recordChangeResponse.getAddedHostAddresses(), new HashSet<>(Collections.singleton(supersetHost)));
        assertEquals(recordChangeResponse.getRemovedHostAddresses(), Collections.emptySet());
    }

    @Test
    void GIVEN_connectivityInformationSubset_WHEN_recordChangesUseCase_THEN_onlyConnectivityDeltaChanges() {
        RecordConnectivityChangesUseCase recordChangeUseCase = useCases.get(RecordConnectivityChangesUseCase.class);

        RecordConnectivityChangesRequest request =
                new RecordConnectivityChangesRequest(defaultSource, connectivityInfoSuperset);

        RecordConnectivityChangesResponse recordChangeResponse = recordChangeUseCase.apply(request);
        assertTrue(recordChangeResponse.didChange()); // Do something with initial response to prevent PMD violation

        request = new RecordConnectivityChangesRequest(defaultSource, sourceConnectivityInfo);

        recordChangeResponse = recordChangeUseCase.apply(request);
        assertTrue(recordChangeResponse.didChange());
        assertEquals(recordChangeResponse.getAddedHostAddresses(), Collections.emptySet());
        assertEquals(recordChangeResponse.getRemovedHostAddresses(), Collections.singleton(supersetHost));
    }

    @Test
    void GIVEN_secondSourceWithConnectivityInformationSubset_WHEN_recordChangesUseCase_THEN_noChange() {
        RecordConnectivityChangesUseCase recordChangeUseCase = useCases.get(RecordConnectivityChangesUseCase.class);

        RecordConnectivityChangesRequest request =
                new RecordConnectivityChangesRequest(defaultSource, connectivityInfoSuperset);

        RecordConnectivityChangesResponse recordChangeResponse = recordChangeUseCase.apply(request);
        assertTrue(recordChangeResponse.didChange()); // Do something with initial response to prevent PMD violation

        request = new RecordConnectivityChangesRequest(ConnectivityInformationSource.CONFIGURATION, sourceConnectivityInfo);

        recordChangeResponse = recordChangeUseCase.apply(request);
        assertFalse(recordChangeResponse.didChange());
        assertEquals(recordChangeResponse.getAddedHostAddresses(), Collections.emptySet());
        assertEquals(recordChangeResponse.getRemovedHostAddresses(), Collections.emptySet());
    }

    @Test
    void GIVEN_emptySetAfterRecordingConnectivityChanges_WHEN_recordChangesUseCase_THEN_connectivityInfoRemoved() {
        RecordConnectivityChangesUseCase recordChangeUseCase = useCases.get(RecordConnectivityChangesUseCase.class);

        RecordConnectivityChangesRequest request =
                new RecordConnectivityChangesRequest(defaultSource, sourceConnectivityInfo);

        RecordConnectivityChangesResponse recordChangeResponse = recordChangeUseCase.apply(request);
        assertTrue(recordChangeResponse.didChange()); // Do something with initial response to prevent PMD violation

        request = new RecordConnectivityChangesRequest(defaultSource, Collections.emptySet());

        recordChangeResponse = recordChangeUseCase.apply(request);
        assertTrue(recordChangeResponse.didChange());
        assertEquals(recordChangeResponse.getAddedHostAddresses(), Collections.emptySet());
        assertEquals(recordChangeResponse.getRemovedHostAddresses(), sourceConnectivityInfo);
    }
}
