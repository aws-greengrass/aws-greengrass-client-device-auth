/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value
@Builder
public class GroupConfiguration {

    @Builder.Default
    ConfigurationFormatVersion version = ConfigurationFormatVersion.MAR_05_2021;

    @Builder.Default
    Map<String, GroupDefinition> groups = Collections.emptyMap();

    @Builder.Default
    Map<String, Map<String, AuthorizationPolicy>> roles = Collections.emptyMap();

    @JsonPOJOBuilder(withPrefix = "")
    public static class GroupConfigurationBuilder {
    }

}
