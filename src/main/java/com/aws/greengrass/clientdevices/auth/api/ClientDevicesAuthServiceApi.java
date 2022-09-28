/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.clientdevices.auth.AuthorizationRequest;
import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.DeviceAuthClient;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.exception.AuthorizationException;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.iot.usecases.VerifyIotCertificate;
import com.aws.greengrass.clientdevices.auth.session.SessionManager;

import java.util.Map;
import javax.inject.Inject;

public class ClientDevicesAuthServiceApi {
    private final SessionManager sessionManager;
    private final DeviceAuthClient deviceAuthClient;
    private final CertificateManager certificateManager;
    private final UseCases useCases;

    /**
     * Constructor.
     *
     * @param sessionManager      session manager
     * @param deviceAuthClient    device auth client
     * @param certificateManager  certificate manager
     * @param useCases            CDA use cases
     */
    @Inject
    public ClientDevicesAuthServiceApi(SessionManager sessionManager,
                                       DeviceAuthClient deviceAuthClient,
                                       CertificateManager certificateManager,
                                       UseCases useCases) {
        this.sessionManager = sessionManager;
        this.deviceAuthClient = deviceAuthClient;
        this.certificateManager = certificateManager;
        this.useCases = useCases; // TODO: Shit, can we do this?
    }

    /**
     * Verify client device identity.
     * @param certificatePem PEM encoded client certificate.
     * @return True if the provided client certificate is trusted.
     */
    public boolean verifyClientDeviceIdentity(String certificatePem) {
        // Allow internal clients to verify their identities
        if (deviceAuthClient.isGreengrassComponent(certificatePem)) {
            return true;
        } else {
            Result<Boolean> result = useCases.get(VerifyIotCertificate.class).apply(certificatePem);
            return result.isOk() && result.get();
        }
    }

    /**
     * Get client auth token.
     * @param credentialType    Type of client credentials
     * @param deviceCredentials Client credential map
     * @return client auth token to be used for future authorization requests.
     * @throws AuthenticationException if unable to authenticate client credentials
     */
    public String getClientDeviceAuthToken(String credentialType, Map<String, String> deviceCredentials)
        throws AuthenticationException {
        return sessionManager.createSession(credentialType, deviceCredentials);
    }

    /**
     * Close client auth session.
     *
     * <P>Note that closing auth sessions is strictly optional</P>
     * @param authToken Auth token corresponding to the session to be closed.
     */
    public void closeClientDeviceAuthSession(String authToken) {
        sessionManager.closeSession(authToken);
    }

    /**
     * Authorize client action.
     * @param authorizationRequest Authorization request, including auth token, operation, and resource
     * @return true if the client action is allowed
     * @throws AuthorizationException if the client action is not allowed
     */
    public boolean authorizeClientDeviceAction(AuthorizationRequest authorizationRequest)
            throws AuthorizationException {
        return deviceAuthClient.canDevicePerform(authorizationRequest);
    }

    /**
     * Subscribe to certificate updates.
     * @param getCertificateRequest subscription request parameters
     * @throws CertificateGenerationException if unable to subscribe to certificate updates
     */
    public void subscribeToCertificateUpdates(GetCertificateRequest getCertificateRequest)
            throws CertificateGenerationException {
        certificateManager.subscribeToCertificateUpdates(getCertificateRequest);
    }

    /**
     * Unsubscribe from certificate updates.
     * @param getCertificateRequest request used to make the original certificate update subscription
     */
    public void unsubscribeFromCertificateUpdates(GetCertificateRequest getCertificateRequest) {
        certificateManager.unsubscribeFromCertificateUpdates(getCertificateRequest);
    }
}
