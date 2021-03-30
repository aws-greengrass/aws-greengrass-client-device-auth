package com.aws.greengrass.device;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class AuthorizationRequest {
    @NonNull String operation;
    @NonNull String resource;
    @NonNull String sessionId;
    @NonNull String clientId;
}
