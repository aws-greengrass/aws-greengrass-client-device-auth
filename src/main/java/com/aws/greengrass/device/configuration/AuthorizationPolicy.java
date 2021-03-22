package com.aws.greengrass.device.configuration;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Set;

@Value
@Builder
@JsonDeserialize(builder = AuthorizationPolicy.AuthorizationPolicyBuilder.class)
public class AuthorizationPolicy {

    String policyId;

    @Builder.Default
    Effect effect = Effect.ALLOW;

    @NonNull Set<String> operations;

    @NonNull Set<String> resources;

    @JsonPOJOBuilder(withPrefix = "")
    public static class AuthorizationPolicyBuilder {
    }

    public enum Effect {
        ALLOW, DENY
    }
}
