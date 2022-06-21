/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.ipc;


import com.aws.greengrass.authorization.AuthorizationHandler;
import com.aws.greengrass.authorization.Permission;
import com.aws.greengrass.authorization.exceptions.AuthorizationException;
import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequest;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.EncryptionUtils;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractSubscribeToCertificateUpdatesOperationHandler;
import software.amazon.awssdk.aws.greengrass.model.CertificateOptions;
import software.amazon.awssdk.aws.greengrass.model.CertificateType;
import software.amazon.awssdk.aws.greengrass.model.CertificateUpdate;
import software.amazon.awssdk.aws.greengrass.model.CertificateUpdateEvent;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;
import software.amazon.awssdk.aws.greengrass.model.ServiceError;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToCertificateUpdatesRequest;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToCertificateUpdatesResponse;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.eventstreamrpc.OperationContinuationHandlerContext;
import software.amazon.awssdk.eventstreamrpc.model.EventStreamJsonMessage;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper.PEM_BOUNDARY_CERTIFICATE;
import static com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper.PEM_BOUNDARY_PRIVATE_KEY;
import static com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper.PEM_BOUNDARY_PUBLIC_KEY;
import static com.aws.greengrass.ipc.common.ExceptionUtil.translateExceptions;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService.SUBSCRIBE_TO_CERTIFICATE_UPDATES;

public class SubscribeToCertificateUpdatesOperationHandler
        extends GeneratedAbstractSubscribeToCertificateUpdatesOperationHandler {
    private static final Logger logger = LogManager.getLogger(SubscribeToCertificateUpdatesOperationHandler.class);
    private static final String COMPONENT_NAME = "componentName";
    private static final String NO_CERT_OPTIONS_ERROR = "Certificate options are required.";
    private static final String INVALID_CERT_TYPE_ERROR = "Valid certificate type is required.";
    private static final String UNAUTHORIZED_ERROR = "Not Authorized";
    private final String serviceName;
    private final CertificateManager certificateManager;
    private final AtomicBoolean subscriptionResponseSent = new AtomicBoolean(false);
    private final AtomicReference<CertificateUpdateEvent> firstStreamingEvent = new AtomicReference<>(null);
    private final AuthorizationHandler authorizationHandler;
    private final GetCertificateRequest getCertificateRequest;
    private final Consumer<com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent> serverCertificateCallback =
            this::forwardServerCertificates;

    /**
     * Constructor.
     *
     * @param context              operation continuation handler
     * @param certificateManager   certificate manager
     * @param authorizationHandler authorization handler
     */
    public SubscribeToCertificateUpdatesOperationHandler(OperationContinuationHandlerContext context,
                                                         CertificateManager certificateManager,
                                                         AuthorizationHandler authorizationHandler) {
        super(context);
        serviceName = context.getAuthenticationData().getIdentityLabel();
        this.certificateManager = certificateManager;
        this.authorizationHandler = authorizationHandler;
        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(GetCertificateRequestOptions.CertificateType.SERVER);
        getCertificateRequest = new GetCertificateRequest(serviceName, requestOptions, serverCertificateCallback);
    }


    @SuppressWarnings("PMD.PreserveStackTrace")
    @Override
    public SubscribeToCertificateUpdatesResponse handleRequest(
            SubscribeToCertificateUpdatesRequest subscribeToCertificateUpdatesRequest) {
        return translateExceptions(() -> {
            try {
                doAuthorizationForSubscribingToCertUpdates(serviceName);
            } catch (AuthorizationException e) {
                logger.atWarn().kv("error", e.getMessage()).kv(COMPONENT_NAME, serviceName).log(UNAUTHORIZED_ERROR);
                throw new UnauthorizedError(e.getMessage());
            }

            CertificateType certificateType = getCertificateTypeFromCertificateOptions(
                    subscribeToCertificateUpdatesRequest.getCertificateOptions());
            if (CertificateType.SERVER.equals(certificateType)) {
                try {
                    certificateManager.subscribeToCertificateUpdates(getCertificateRequest);
                } catch (CertificateGenerationException e) {
                    logger.atError().cause(e).log("Unable to subscribe to the certificate updates.");
                    throw new ServiceError(
                            "Subscribe to certificate updates failed. Check Greengrass log for details.");
                }
            }
            return new SubscribeToCertificateUpdatesResponse();
        });
    }

    /**
     * Checks if the component is authorized to make IPC calls for subscribing to cert updates.
     *
     * @param serviceName component name
     * @throws AuthorizationException if the component is not authorized
     */
    private void doAuthorizationForSubscribingToCertUpdates(String serviceName) throws AuthorizationException {
        authorizationHandler.isAuthorized(
                ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME,
                Permission.builder()
                        .principal(serviceName)
                        .operation(SUBSCRIBE_TO_CERTIFICATE_UPDATES)
                        .resource("*")
                        .build());
    }

    private CertificateType getCertificateTypeFromCertificateOptions(CertificateOptions certificateOptions) {
        validateCertificateOptions(certificateOptions);
        CertificateType certificateType = certificateOptions.getCertificateType();
        if (certificateType == null) {
            logger.atError().kv(COMPONENT_NAME, serviceName).log(INVALID_CERT_TYPE_ERROR);
            throw new InvalidArgumentsError(INVALID_CERT_TYPE_ERROR);
        }
        return certificateType;
    }

    private void validateCertificateOptions(CertificateOptions certificateOptions) {
        if (certificateOptions == null) {
            logger.atError().kv(COMPONENT_NAME, serviceName).log(NO_CERT_OPTIONS_ERROR);
            throw new InvalidArgumentsError(NO_CERT_OPTIONS_ERROR);
        }
    }
    
    @SuppressWarnings("PMD.PreserveStackTrace")
    private void forwardServerCertificates(
            com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent updateEvent) {
        CertificateUpdate certificateUpdate = new CertificateUpdate();
        try {
            KeyPair kp = updateEvent.getKeyPair();
            certificateUpdate
                    .withCertificate(EncryptionUtils.encodeToPem(PEM_BOUNDARY_CERTIFICATE,
                            updateEvent.getCertificate().getEncoded()))
                    .withCaCertificates(this.certificateManager.getCACertificates())
                    .withPublicKey(
                            EncryptionUtils.encodeToPem(PEM_BOUNDARY_PUBLIC_KEY, kp.getPublic().getEncoded()))
                    .withPrivateKey(
                            EncryptionUtils.encodeToPem(PEM_BOUNDARY_PRIVATE_KEY, kp.getPrivate().getEncoded()));
        } catch (CertificateEncodingException | IOException | KeyStoreException e) {
            logger.atError().cause(e).log("Unable to attach certificates to the response");
            throw new ServiceError("Subscribe to certificate update failed. Check Greengrass log for details.");
        }
        CertificateUpdateEvent event = new CertificateUpdateEvent();
        event.setCertificateUpdate(certificateUpdate);

        // The subscription request has two types of event responses where the first response sent must be of type
        // 'SubscribeToCertificateUpdatesResponse' and the following streaming responses must be of type
        // 'CertificateUpdateEvent'.

        // Since the first streaming response of CertificateUpdateEvent is created before we send the
        // SubscribeToCertificateUpdatesResponse, we store and send that event in the afterHandleRequest.
        synchronized (firstStreamingEvent) {
            if (subscriptionResponseSent.get()) {
                this.sendStreamEvent(event);
            } else {
                firstStreamingEvent.set(event);
            }
        }
    }

    @Override
    public void handleStreamEvent(EventStreamJsonMessage eventStreamJsonMessage) {
    }

    /**
     * <p>
     * Here, since this method is called after handling the request successfully, we know that the initial
     * SubscribeToCertificateUpdatesResponse response is sent. So, we send the first streaming response of
     * CertificateUpdateEvent.
     * </p>
     */
    @Override
    public void afterHandleRequest() {
        synchronized (firstStreamingEvent) {
            if (subscriptionResponseSent.compareAndSet(false, true)) {
                CertificateUpdateEvent event = firstStreamingEvent.get();
                if (event != null) {
                    sendStreamEvent(event);
                }
                firstStreamingEvent.set(null);
            }
        }
    }

    @Override
    protected void onStreamClosed() {
        certificateManager.unsubscribeFromCertificateUpdates(getCertificateRequest);
    }
}
