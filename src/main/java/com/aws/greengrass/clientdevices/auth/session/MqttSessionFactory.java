/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.DeviceAuthClient;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.Component;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.ThingRegistry;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

public class MqttSessionFactory implements SessionFactory {
    private final DeviceAuthClient deviceAuthClient;
    private final CertificateRegistry certificateRegistry;
    private final ThingRegistry thingRegistry;
    private final SessionConfig sessionConfig;

    /**
     * Constructor.
     *
     * @param deviceAuthClient    Device auth client
     * @param certificateRegistry device Certificate registry
     * @param thingRegistry       thing registry
     * @param sessionConfig       Session configuration
     */
    @Inject
    public MqttSessionFactory(DeviceAuthClient deviceAuthClient,
                              CertificateRegistry certificateRegistry,
                              ThingRegistry thingRegistry,
                              SessionConfig sessionConfig) {
        this.deviceAuthClient = deviceAuthClient;
        this.certificateRegistry = certificateRegistry;
        this.thingRegistry = thingRegistry;
        this.sessionConfig = sessionConfig;
    }

    @Override
    public Session createSession(Map<String, String> credentialMap) throws AuthenticationException {
        // TODO: replace with jackson object mapper
        MqttCredential mqttCredential = new MqttCredential(credentialMap);

        boolean isGreengrassComponent = deviceAuthClient.isGreengrassComponent(mqttCredential.certificatePem);
        if (isGreengrassComponent) {
            return createGreengrassComponentSession();
        }

        return createIotThingSession(mqttCredential);
    }

    private Session createIotThingSession(MqttCredential mqttCredential) throws AuthenticationException {
        try {
            Optional<Certificate> cert = certificateRegistry.getCertificateFromPem(mqttCredential.certificatePem);
            if (!cert.isPresent() || !isWithinTrustDuration(cert.get().getStatusLastUpdated())) {
                throw new AuthenticationException("Certificate isn't active");
            }
            Thing thing = thingRegistry.getOrCreateThing(mqttCredential.clientId);
            if (!thingRegistry.isThingAttachedToCertificate(thing, cert.get())
                    || !isWithinTrustDuration(thing.getAttachedCertificateIds().get(cert.get().getCertificateId()))) {
                throw new AuthenticationException("unable to authenticate device");
            }
            return new SessionImpl(cert.get(), thing);
        } catch (CloudServiceInteractionException | InvalidCertificateException e) {
            throw new AuthenticationException("Failed to verify certificate with cloud", e);
        }
    }

    private Session createGreengrassComponentSession() {
        return new SessionImpl(new Component());
    }

    private boolean isWithinTrustDuration(Instant lastVerifiedInstant) {
        if (lastVerifiedInstant == null) {
            return false;
        }
        Instant validTill = lastVerifiedInstant.plus(
                sessionConfig.getClientDeviceTrustDurationHours(), ChronoUnit.HOURS);
        return validTill.isAfter(Instant.now());
    }

    private static class MqttCredential {
        private final String clientId;
        private final String certificatePem;
        private final String username;
        private final String password;

        public MqttCredential(Map<String, String> credentialMap) {
            this.clientId = credentialMap.get("clientId");
            this.certificatePem = credentialMap.get("certificatePem");
            this.username = credentialMap.get("username");
            this.password = credentialMap.get("password");
        }
    }
}
