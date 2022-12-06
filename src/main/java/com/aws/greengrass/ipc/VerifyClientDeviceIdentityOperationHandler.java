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
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.metrics.VerifyClientDeviceIdentityEvent;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Utils;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractVerifyClientDeviceIdentityOperationHandler;
import software.amazon.awssdk.aws.greengrass.model.ClientDeviceCredential;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;
import software.amazon.awssdk.aws.greengrass.model.ServiceError;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.aws.greengrass.model.VerifyClientDeviceIdentityRequest;
import software.amazon.awssdk.aws.greengrass.model.VerifyClientDeviceIdentityResponse;
import software.amazon.awssdk.eventstreamrpc.OperationContinuationHandlerContext;
import software.amazon.awssdk.eventstreamrpc.model.EventStreamJsonMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static com.aws.greengrass.ipc.common.ExceptionUtil.translateExceptions;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService.VERIFY_CLIENT_DEVICE_IDENTITY;

public class VerifyClientDeviceIdentityOperationHandler
        extends GeneratedAbstractVerifyClientDeviceIdentityOperationHandler {
    private static final Logger logger = LogManager.getLogger(VerifyClientDeviceIdentityOperationHandler.class);
    private static final String COMPONENT_NAME = "componentName";
    private static final String UNAUTHORIZED_ERROR = "Not Authorized";
    private static final String NO_DEVICE_CREDENTIAL_ERROR = "Client device credential is required";
    private static final String NO_DEVICE_CERTIFICATE_ERROR = "Client device certificate is required";
    private final ClientDevicesAuthServiceApi clientDevicesAuthServiceApi;
    private final String serviceName;
    private final AuthorizationHandler authorizationHandler;
    private final ExecutorService cloudCallThreadPool;
    private final DomainEvents domainEvents;

    /**
     * Constructor.
     *
     * @param context                     operation continuation handler
     * @param clientDevicesAuthServiceApi client devices auth service handle
     * @param authorizationHandler        authorization handler
     * @param cloudCallThreadPool         executor to run the call to the cloud asynchronously
     * @param domainEvents                metric event emitter
     */
    public VerifyClientDeviceIdentityOperationHandler(OperationContinuationHandlerContext context,
                                                      ClientDevicesAuthServiceApi clientDevicesAuthServiceApi,
                                                      AuthorizationHandler authorizationHandler,
                                                      ExecutorService cloudCallThreadPool, DomainEvents domainEvents) {

        super(context);
        this.clientDevicesAuthServiceApi = clientDevicesAuthServiceApi;
        this.authorizationHandler = authorizationHandler;
        this.cloudCallThreadPool = cloudCallThreadPool;
        serviceName = context.getAuthenticationData().getIdentityLabel();
        this.domainEvents = domainEvents;
    }

    @Override
    public CompletableFuture<VerifyClientDeviceIdentityResponse> handleRequestAsync(
            VerifyClientDeviceIdentityRequest request) {
        try {
            return CompletableFuture.supplyAsync(() -> handleRequest(request), cloudCallThreadPool);
        } catch (RejectedExecutionException e) {
            CompletableFuture<VerifyClientDeviceIdentityResponse> fut = new CompletableFuture<>();
            logger.atWarn().kv(COMPONENT_NAME, serviceName)
                    .log("Unable to queue VerifyClientDeviceIdentity. {}", e.getMessage());
            fut.completeExceptionally(new ServiceError("Unable to queue request"));
            return fut;
        }
    }

    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.PreserveStackTrace"})
    @Override
    public VerifyClientDeviceIdentityResponse handleRequest(VerifyClientDeviceIdentityRequest request) {
        return translateExceptions(() -> {
            try {
                doAuthorizationForClientDevIdentity();
            } catch (AuthorizationException e) {
                domainEvents.emit(new VerifyClientDeviceIdentityEvent(
                        VerifyClientDeviceIdentityEvent.VerificationStatus.FAIL));
                logger.atWarn().kv("error", e.getMessage()).kv(COMPONENT_NAME, serviceName).log(UNAUTHORIZED_ERROR);
                throw new UnauthorizedError(e.getMessage());
            }
            String certificate = getCertificateFromCredential(request.getCredential());
            try {
                VerifyClientDeviceIdentityResponse response = new VerifyClientDeviceIdentityResponse();
                response.withIsValidClientDevice(clientDevicesAuthServiceApi.verifyClientDeviceIdentity(certificate));
                return response;
            } catch (Exception e) {
                domainEvents.emit(new VerifyClientDeviceIdentityEvent(
                        VerifyClientDeviceIdentityEvent.VerificationStatus.FAIL));
                logger.atError().cause(e).log("Unable to verify client device identity");
                throw new ServiceError("Verifying client device identity failed. Check Greengrass log for details.");
            }
        });
    }

    private void doAuthorizationForClientDevIdentity() throws AuthorizationException {
        authorizationHandler.isAuthorized(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME,
                Permission.builder().principal(serviceName).operation(VERIFY_CLIENT_DEVICE_IDENTITY).resource("*")
                        .build());
    }

    private String getCertificateFromCredential(ClientDeviceCredential credential) {
        if (credential == null) {
            throw new InvalidArgumentsError(NO_DEVICE_CREDENTIAL_ERROR);
        }
        String certificate = credential.getClientDeviceCertificate();
        if (Utils.isEmpty(certificate)) {
            throw new InvalidArgumentsError(NO_DEVICE_CERTIFICATE_ERROR);
        }
        // If the certificate PEM is only the encoded data without headers, re-encode it into
        // the format that IoT Core needs.
        return IPCUtils.reEncodeCertToPem(certificate);
    }

    @Override
    public void handleStreamEvent(EventStreamJsonMessage eventStreamJsonMessage) {

    }

    @Override
    protected void onStreamClosed() {

    }
}
