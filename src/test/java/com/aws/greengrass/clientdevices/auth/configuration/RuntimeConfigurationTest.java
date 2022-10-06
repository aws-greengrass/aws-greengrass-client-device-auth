/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.iot.dto.ThingV1;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.utils.ImmutableMap;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class RuntimeConfigurationTest {
    private Topics configurationTopics;
    private RuntimeConfiguration runtimeConfiguration;

    @BeforeEach
    void beforeEach() {
        configurationTopics = Topics.of(new Context(), "config", null);
        runtimeConfiguration = RuntimeConfiguration.from(configurationTopics);
    }

    @AfterEach
    void afterEach() throws IOException {
        configurationTopics.getContext().close();
    }

    @Test
    void GIVEN_emptyRegistry_WHEN_putThingV1_THEN_thingIsReadable() {
        Map<String, Long> certMap = ImmutableMap.of("certId", 0L);
        ThingV1 thingDTO = new ThingV1("Thing", certMap);

        runtimeConfiguration.putThing(thingDTO);

        // Read it back and ensure it equals
        ThingV1 readThing = runtimeConfiguration.getThingV1("Thing").get();
        assertThat(thingDTO, equalTo(readThing));

        // Try again with new runtime configuration
        runtimeConfiguration = RuntimeConfiguration.from(configurationTopics);
        ThingV1 readThing2 = runtimeConfiguration.getThingV1("Thing").get();
        assertThat(thingDTO, equalTo(readThing2));
    }

    @Test
    void GIVEN_emptyRegistry_WHEN_getThingV1_THEN_emptyOptionalReturned() {
        assertThat(runtimeConfiguration.getThingV1("Thing"), equalTo(Optional.empty()));
    }

    @Test
    void GIVEN_nullThing_WHEN_putThing_THEN_doesntThrow() {
        runtimeConfiguration.putThing(null);
    }

    // TODO: Add test for reading Thing with missing configuration
    // TODO: Cert status index OOB (<0, >ACTIVE)
}