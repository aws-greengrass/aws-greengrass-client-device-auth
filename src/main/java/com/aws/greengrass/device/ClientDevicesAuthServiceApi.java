/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.iot.CertificateRegistry;
import com.aws.greengrass.device.session.SessionManager;

import java.util.Map;
import javax.inject.Inject;

public class ClientDevicesAuthServiceApi {
    private final CertificateRegistry certificateRegistry;
    private final SessionManager sessionManager;
    private final DeviceAuthClient deviceAuthClient;

    /**
     * Constructor.
     *
     * @param certificateRegistry iot auth client
     * @param sessionManager      session manager
     * @param deviceAuthClient    device auth client
     */
    @Inject
    public ClientDevicesAuthServiceApi(CertificateRegistry certificateRegistry,
                                       SessionManager sessionManager,
                                       DeviceAuthClient deviceAuthClient) {
        this.certificateRegistry = certificateRegistry;
        this.sessionManager = sessionManager;
        this.deviceAuthClient = deviceAuthClient;
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
            return certificateRegistry.isCertificateValid(certificatePem);
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
}
