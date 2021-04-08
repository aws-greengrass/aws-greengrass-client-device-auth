/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.device.iot.Thing;

import javax.inject.Inject;

public class DeviceAuthClient {

    private final SessionManager sessionManager;
    private final GroupManager groupManager;
    private final IotAuthClient iotAuthClient;

    /**
     * Constructor.
     *
     * @param sessionManager Session manager
     * @param groupManager   Group manager
     * @param iotAuthClient  Iot auth client
     */
    @Inject
    public DeviceAuthClient(SessionManager sessionManager, GroupManager groupManager, IotAuthClient iotAuthClient) {
        this.sessionManager = sessionManager;
        this.groupManager = groupManager;
        this.iotAuthClient = iotAuthClient;
    }

    public String createSession(String certificatePem) {
        return sessionManager.createSession(new Certificate(certificatePem, iotAuthClient));
    }

    /**
     * Determine whether the requested device operation is allowed.
     *
     * @param request authorization request including operation, resource, sessionId, clientId
     * @return if device is authorized
     * @throws AuthorizationException if session is invalid
     */
    public boolean canDevicePerform(AuthorizationRequest request) throws AuthorizationException {
        Session session = sessionManager.findSession(request.getSessionId());
        if (session == null) {
            throw new AuthorizationException(
                    String.format("invalid session id (%s)", request.getSessionId()));
        }

        Certificate certificate = (Certificate) session.get(Certificate.NAMESPACE);
        Thing thing = new Thing(request.getClientId());
        // if thing name is already cached, proceed;
        // otherwise validate thing name with certificate, then cache thing name
        session.computeIfAbsent(thing.getNamespace(),
                (k) -> iotAuthClient.isThingAttachedToCertificate(thing, certificate) ? thing : null);
        return PermissionEvaluationUtils.isAuthorized(request.getOperation(), request.getResource(),
                groupManager.getApplicablePolicyPermissions(session));
    }
}
