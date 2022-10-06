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
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration.THINGS_CERTIFICATES_KEY;
import static com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration.THINGS_KEY;
import static com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration.THINGS_V1_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class RuntimeConfigurationTest {
    private static final String mockThingName = "mock-thing";
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
        ThingV1 thingDTO = new ThingV1(mockThingName, certMap);

        runtimeConfiguration.putThing(thingDTO);

        // Read it back and ensure it equals
        ThingV1 readThing = runtimeConfiguration.getThingV1(mockThingName).get();
        assertThat(thingDTO, equalTo(readThing));

        // Try again with new runtime configuration
        runtimeConfiguration = RuntimeConfiguration.from(configurationTopics);
        ThingV1 readThing2 = runtimeConfiguration.getThingV1(mockThingName).get();
        assertThat(thingDTO, equalTo(readThing2));
    }

    @Test
    void GIVEN_emptyRegistry_WHEN_getThingV1_THEN_emptyOptionalReturned() {
        assertThat(runtimeConfiguration.getThingV1(mockThingName), equalTo(Optional.empty()));
    }

    @Test
    void GIVEN_missingConfig_WHEN_getThingV1_THEN_returnDefault() {
        // create thing config with missing children (details)
        configurationTopics.lookupTopics(THINGS_KEY, THINGS_V1_KEY, mockThingName);
        ThingV1 readThing = runtimeConfiguration.getThingV1(mockThingName).get();
        assertNotNull(readThing);
        assertThat(readThing.getThingName(), is(mockThingName));
        assertThat(readThing.getCertificates(), is(Collections.emptyMap()));
    }

    @Test
    void GIVEN_malformedConfig_WHEN_putThingV1_THEN_returnDefault() {
        // create thing leaf node (topic) instead of topic container (topics)
        String[] topicList = {THINGS_KEY, THINGS_V1_KEY, mockThingName, THINGS_CERTIFICATES_KEY};
        for (int index = 0; index < topicList.length; index++) {
            String[] temp = Arrays.copyOfRange(topicList, 0, index+1);
            configurationTopics.lookup(temp);
            System.out.println(configurationTopics.toPOJO());
            ThingV1 thing = new ThingV1(mockThingName, Collections.emptyMap());
            runtimeConfiguration.putThing(thing);
            ThingV1 readThing = runtimeConfiguration.getThingV1(mockThingName).get();
            assertNotNull(readThing);
            assertThat(readThing.getThingName(), is(mockThingName));
            assertThat(readThing.getCertificates(), is(Collections.emptyMap()));

            configurationTopics = Topics.of(configurationTopics.context, "config", null);
        }
    }

    // TODO: Cert status index OOB (<0, >ACTIVE)
}