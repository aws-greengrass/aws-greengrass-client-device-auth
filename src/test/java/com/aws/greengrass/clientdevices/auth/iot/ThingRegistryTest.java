/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class ThingRegistryTest {
    private static final String mockThingName = "mock-thing";
    private Topics configTopics;
    private DomainEvents domainEvents;
    private ThingRegistry registry;

    @BeforeEach
    void beforeEach() {
        domainEvents = new DomainEvents();
        configTopics = Topics.of(new Context(), "config", null);
        registry = new ThingRegistry(domainEvents, RuntimeConfiguration.from(configTopics));
    }

    @AfterEach
    void afterEach() throws IOException {
        configTopics.context.close();
    }

    @Test
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    void GIVEN_emptyRegistry_WHEN_createThing_THEN_thingIsAddedToRegistry() {
        Thing createdThing = registry.createThing(mockThingName);
        Thing retrievedThing = registry.getThing(mockThingName);

        assertThat(createdThing.getThingName(), is(mockThingName));
        assertThat(createdThing.getAttachedCertificateIds(), equalTo(Collections.emptyMap()));

        assertThat(createdThing, equalTo(retrievedThing));
        // IMPORTANT! Ensure a copy is returned. It is incorrect to call the equals method for this
        assertThat(createdThing != retrievedThing, is(true));

        // TODO: check ThingUpdated event
    }

    @Test
    void GIVEN_unmodifiedThing_WHEN_updateThing_THEN_oldThingReturned() {
        Thing createdThing = registry.createThing(mockThingName);
        Thing returnedThing = registry.updateThing(createdThing);
        assertThat(createdThing, equalTo(returnedThing));

        // TODO: no update event
    }
}
