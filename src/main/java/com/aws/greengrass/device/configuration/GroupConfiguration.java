package com.aws.greengrass.device.configuration;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class GroupConfiguration {

    @NonNull ConfigurationFormatVersion version;

    @NonNull Map<String, GroupDefinition> groups;

    @NonNull Map<String, Map<String, AuthorizationPolicy>> roles;

    @JsonPOJOBuilder(withPrefix = "")
    public static class GroupConfigurationBuilder {
    }

}
