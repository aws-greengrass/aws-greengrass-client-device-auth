/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;


import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


@ExtendWith({MockitoExtension.class, GGExtension.class})
class ThingConfigStoreAdapterTest {
    private static final String mockThingName = "mock-thing";
    private static final List<String> mockAttachedCertIds = Arrays.asList("cert-1", "cert-2", "cert-3");

    private Topics configTopic;
    private ThingConfigStoreAdapter configStoreAdapter;

    @BeforeEach
    void beforeEach() {
        configTopic = Topics.of(new Context(), "config", null);
        configStoreAdapter = new ThingConfigStoreAdapter(RuntimeConfiguration.from(configTopic));
    }

    @AfterEach
    void afterEach() throws IOException {
        configTopic.context.close();
    }

    @Test
    void GIVEN_validThingName_WHEN_getThing_THEN_validThingReturned() {
        Thing createdThing = Thing.of(1, mockThingName, mockAttachedCertIds);
        configStoreAdapter.createThing(createdThing);
        Thing retrievedThing = configStoreAdapter.getThing(mockThingName).get();
        assertNotNull(retrievedThing);
        assertEquals(retrievedThing, createdThing);
    }

    @Test
    void GIVEN_validThing_WHEN_updateThing_THEN_thingUpdated() {
        Thing createdThing = Thing.of(1, mockThingName, mockAttachedCertIds);
        configStoreAdapter.createThing(createdThing);
        Thing updatedThing = Thing.of(2, mockThingName, Collections.emptyList());
        configStoreAdapter.createThing(updatedThing);
        Thing retrievedThing = configStoreAdapter.getThing(mockThingName).get();
        assertNotNull(retrievedThing);
        assertEquals(retrievedThing, updatedThing);
    }

    @Test
    void GIVEN_invalidThingName_WHEN_getThing_THEN_emptyOptionalReturned() {
        assertThat(configStoreAdapter.getThing(mockThingName), is(Optional.empty()));
        assertThat(configStoreAdapter.getThing(null), is(Optional.empty()));
        assertThat(configStoreAdapter.getThing(""), is(Optional.empty()));
    }

    @Test
    void GIVEN_invalidThing_WHEN_updateThing_THEN_exceptionThrown() {
        assertThrows(IllegalArgumentException.class, () -> configStoreAdapter.createThing(null));
    }

}
