/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.ipc;


import com.aws.greengrass.authorization.AuthorizationHandler;
import com.aws.greengrass.authorization.Permission;
import com.aws.greengrass.authorization.exceptions.AuthorizationException;
import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractGetClientDeviceAuthTokenOperationHandler;
import software.amazon.awssdk.aws.greengrass.model.CredentialDocument;
import software.amazon.awssdk.aws.greengrass.model.GetClientDeviceAuthTokenRequest;
import software.amazon.awssdk.aws.greengrass.model.GetClientDeviceAuthTokenResponse;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;
import software.amazon.awssdk.aws.greengrass.model.InvalidCredentialError;
import software.amazon.awssdk.aws.greengrass.model.MQTTCredential;
import software.amazon.awssdk.aws.greengrass.model.ServiceError;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.eventstreamrpc.OperationContinuationHandlerContext;
import software.amazon.awssdk.eventstreamrpc.model.EventStreamJsonMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static com.aws.greengrass.ipc.common.ExceptionUtil.translateExceptions;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService.GET_CLIENT_DEVICE_AUTH_TOKEN;

public class GetClientDeviceAuthTokenOperationHandler
        extends GeneratedAbstractGetClientDeviceAuthTokenOperationHandler {
    private static final Logger logger = LogManager.getLogger(GetClientDeviceAuthTokenOperationHandler.class);
    private static final String COMPONENT_NAME = "componentName";
    private static final String UNAUTHORIZED_ERROR = "Not Authorized";
    private static final String MQTT_CREDENTIAL_TYPE = "mqtt";
    private static final String NO_DEVICE_CREDENTIAL_ERROR = "Invalid client device credentials";
    private final String serviceName;
    private final AuthorizationHandler authorizationHandler;
    private final ClientDevicesAuthServiceApi clientDevicesAuthServiceApi;
    private final Map<String, String> credentialMap = new HashMap<>();
    private final Executor cloudCallThreadPool;

    /**
     * Constructor.
     *
     * @param context                     operation continuation handler
     * @param clientDevicesAuthServiceApi client devices auth service handle
     * @param authorizationHandler        authorization handler
     * @param cloudCallThreadPool         executor to run the call to the cloud asynchronously
     */
    public GetClientDeviceAuthTokenOperationHandler(
            OperationContinuationHandlerContext context,
            ClientDevicesAuthServiceApi clientDevicesAuthServiceApi,
            AuthorizationHandler authorizationHandler,
            Executor cloudCallThreadPool
    ) {

        super(context);
        serviceName = context.getAuthenticationData().getIdentityLabel();
        this.clientDevicesAuthServiceApi = clientDevicesAuthServiceApi;
        this.authorizationHandler = authorizationHandler;
        this.cloudCallThreadPool = cloudCallThreadPool;
    }

    @Override
    public CompletableFuture<GetClientDeviceAuthTokenResponse> handleRequestAsync(
            GetClientDeviceAuthTokenRequest request) {
        try {
            return CompletableFuture.supplyAsync(() -> handleRequest(request), cloudCallThreadPool);
        } catch (RejectedExecutionException e) {
            CompletableFuture<GetClientDeviceAuthTokenResponse> fut = new CompletableFuture<>();
            logger.atWarn().kv(COMPONENT_NAME, serviceName)
                    .log("Unable to queue GetClientDeviceAuthTokenResponse. {}", e.getMessage());
            fut.completeExceptionally(new ServiceError("Unable to queue request"));
            return fut;
        }
    }

    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.PreserveStackTrace"})
    @Override
    public GetClientDeviceAuthTokenResponse handleRequest(GetClientDeviceAuthTokenRequest request) {
        return translateExceptions(() -> {
            try {
                doAuthorizationForClientDevAuthToken();
            } catch (AuthorizationException e) {
                logger.atWarn().kv("error", e.getMessage()).kv(COMPONENT_NAME, serviceName).log(UNAUTHORIZED_ERROR);
                throw new UnauthorizedError(e.getMessage());
            }
            Map<String, String> credentialMap = mapOfMqttCredential(request.getCredential());
            try {
                String sessionId = clientDevicesAuthServiceApi.getClientDeviceAuthToken(MQTT_CREDENTIAL_TYPE,
                        credentialMap);
                GetClientDeviceAuthTokenResponse response = new GetClientDeviceAuthTokenResponse();
                return response.withClientDeviceAuthToken(sessionId);
            } catch (AuthenticationException e) {
                logger.atError().cause(e).log("Unable to authenticate the client device with the given credentials");
                throw new InvalidCredentialError(
                        "Unable to authenticate the client device with the given credentials."
                                + " Check Greengrass log for details.");
            } catch (Exception e) {
                logger.atError().cause(e).log("Unable to get client device auth token from the session");
                throw new ServiceError(
                        "Getting client device auth token failed. Check Greengrass log for details.");
            }
        });
    }

    @SuppressWarnings("PMD.PreserveStackTrace")
    private MQTTCredential validateMqttCredentials(CredentialDocument credentialDocument) {
        if (credentialDocument == null || credentialDocument.getMqttCredential() == null) {
            throw new InvalidArgumentsError(NO_DEVICE_CREDENTIAL_ERROR);
        }
        return credentialDocument.getMqttCredential();
    }

    private Map<String, String> mapOfMqttCredential(CredentialDocument credentialDocument) {
        MQTTCredential mqttCredential = validateMqttCredentials(credentialDocument);
        credentialMap.put("clientId", mqttCredential.getClientId());
        credentialMap.put("username", mqttCredential.getUsername());
        credentialMap.put("password", mqttCredential.getPassword());
        String certificatePem = mqttCredential.getCertificatePem();
        // If the certificate PEM is only the encoded data without headers, re-encode it into
        // the format that IoT Core needs.
        credentialMap.put("certificatePem", IPCUtils.reEncodeCertToPem(certificatePem));
        return credentialMap;
    }

    private void doAuthorizationForClientDevAuthToken() throws AuthorizationException {
        authorizationHandler.isAuthorized(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME,
                Permission.builder()
                        .principal(serviceName)
                        .operation(GET_CLIENT_DEVICE_AUTH_TOKEN)
                        .resource("*")
                        .build());
    }

    @Override
    public void handleStreamEvent(EventStreamJsonMessage eventStreamJsonMessage) {
    }

    @Override
    protected void onStreamClosed() {
    }
}
