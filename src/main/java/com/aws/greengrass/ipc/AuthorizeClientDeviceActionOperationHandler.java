/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.ipc;


import com.aws.greengrass.authorization.AuthorizationHandler;
import com.aws.greengrass.authorization.Permission;
import com.aws.greengrass.authorization.exceptions.AuthorizationException;
import com.aws.greengrass.clientdevices.auth.AuthorizationRequest;
import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.exception.InvalidSessionException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Utils;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractAuthorizeClientDeviceActionOperationHandler;
import software.amazon.awssdk.aws.greengrass.model.AuthorizeClientDeviceActionRequest;
import software.amazon.awssdk.aws.greengrass.model.AuthorizeClientDeviceActionResponse;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;
import software.amazon.awssdk.aws.greengrass.model.InvalidClientDeviceAuthTokenError;
import software.amazon.awssdk.aws.greengrass.model.ServiceError;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.eventstreamrpc.OperationContinuationHandlerContext;
import software.amazon.awssdk.eventstreamrpc.model.EventStreamJsonMessage;

import static com.aws.greengrass.ipc.common.ExceptionUtil.translateExceptions;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService.AUTHORIZE_CLIENT_DEVICE_ACTION;

public class AuthorizeClientDeviceActionOperationHandler
        extends GeneratedAbstractAuthorizeClientDeviceActionOperationHandler {
    private static final Logger logger = LogManager.getLogger(AuthorizeClientDeviceActionOperationHandler.class);
    private static final String COMPONENT_NAME = "componentName";
    private static final String UNAUTHORIZED_ERROR = "Not Authorized";
    private static final String NO_AUTH_TOKEN_ERROR = "Auth token is required";
    private static final String NO_OPERATION_ERROR = "Operation is required";
    private static final String NO_RESOURCE_ERROR = "Resource is required";
    private final String serviceName;
    private final AuthorizationHandler authorizationHandler;
    private final ClientDevicesAuthServiceApi clientDevicesAuthServiceApi;

    /**
     * Constructor.
     *
     * @param context                     operation continuation handler
     * @param clientDevicesAuthServiceApi client devices auth service handle
     * @param authorizationHandler        authorization handler
     */
    public AuthorizeClientDeviceActionOperationHandler(
            OperationContinuationHandlerContext context,
            ClientDevicesAuthServiceApi clientDevicesAuthServiceApi,
            AuthorizationHandler authorizationHandler
    ) {

        super(context);
        serviceName = context.getAuthenticationData().getIdentityLabel();
        this.authorizationHandler = authorizationHandler;
        this.clientDevicesAuthServiceApi = clientDevicesAuthServiceApi;
    }

    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.PreserveStackTrace"})
    @Override
    public AuthorizeClientDeviceActionResponse handleRequest(AuthorizeClientDeviceActionRequest request) {
        return translateExceptions(() -> {
            try {
                doAuthorizationForClientDevAction();
            } catch (AuthorizationException e) {
                logger.atWarn().kv("error", e.getMessage()).kv(COMPONENT_NAME, serviceName).log(UNAUTHORIZED_ERROR);
                throw new UnauthorizedError(e.getMessage());
            }
            AuthorizationRequest authorizationRequest = getAuthzRequest(request);
            try {
                boolean isAuthorized = clientDevicesAuthServiceApi.authorizeClientDeviceAction(authorizationRequest);
                AuthorizeClientDeviceActionResponse response = new AuthorizeClientDeviceActionResponse();
                return response.withIsAuthorized(isAuthorized);
            } catch (InvalidSessionException e) {
                logger.atWarn().log("Unable to find a valid session with the given auth token");
                throw new InvalidClientDeviceAuthTokenError(
                        "Unable to find a valid session with the given auth token. Check Greengrass log for details.");
            } catch (Exception e) {
                logger.atError().cause(e).log("Unhandled exception while authorizing client device action");
                throw new ServiceError(
                        "Authorizing client device action failed. Check Greengrass log for details.");
            }
        });
    }

    private void doAuthorizationForClientDevAction() throws AuthorizationException {
        authorizationHandler.isAuthorized(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME,
                Permission.builder()
                        .principal(serviceName)
                        .operation(AUTHORIZE_CLIENT_DEVICE_ACTION)
                        .resource("*")
                        .build());
    }


    private AuthorizationRequest getAuthzRequest(AuthorizeClientDeviceActionRequest request) {
        validateRequest(request);
        return AuthorizationRequest.builder()
                .sessionId(request.getClientDeviceAuthToken())
                .operation(request.getOperation())
                .resource(request.getResource())
                .build();
    }

    private void validateRequest(AuthorizeClientDeviceActionRequest request) {
        if (Utils.isEmpty(request.getClientDeviceAuthToken())) {
            throw new InvalidArgumentsError(NO_AUTH_TOKEN_ERROR);
        }
        if (Utils.isEmpty(request.getOperation())) {
            throw new InvalidArgumentsError(NO_OPERATION_ERROR);
        }
        if (Utils.isEmpty(request.getResource())) {
            throw new InvalidArgumentsError(NO_RESOURCE_ERROR);
        }
    }

    @Override
    public void handleStreamEvent(EventStreamJsonMessage eventStreamJsonMessage) {
    }

    @Override
    protected void onStreamClosed() {
    }
}
