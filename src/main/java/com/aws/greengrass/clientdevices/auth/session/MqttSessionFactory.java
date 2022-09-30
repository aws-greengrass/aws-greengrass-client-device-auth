/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.DeviceAuthClient;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.Component;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.registry.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.registry.ThingRegistry;

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

public class MqttSessionFactory implements SessionFactory {
    private final DeviceAuthClient deviceAuthClient;
    private final CertificateRegistry certificateRegistry;
    private final ThingRegistry thingRegistry;

    /**
     * Constructor.
     *
     * @param deviceAuthClient    Device auth client
     * @param certificateRegistry device Certificate registry
     * @param thingRegistry       thing registry
     */
    @Inject
    public MqttSessionFactory(DeviceAuthClient deviceAuthClient,
                              CertificateRegistry certificateRegistry,
                              ThingRegistry thingRegistry) {
        this.deviceAuthClient = deviceAuthClient;
        this.certificateRegistry = certificateRegistry;
        this.thingRegistry = thingRegistry;
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
        Optional<String> certificateId;
        try {
            certificateId = certificateRegistry.getIotCertificateIdForPem(mqttCredential.certificatePem);
            if (!certificateId.isPresent()) {
                throw new AuthenticationException("Certificate isn't active");
            }
            Thing thing = thingRegistry.getOrCreateThing(mqttCredential.clientId);
            Certificate cert = new Certificate(certificateId.get());
            if (!thingRegistry.isThingAttachedToCertificate(thing, cert)) {
                throw new AuthenticationException("unable to authenticate device");
            }
            return new SessionImpl(cert, thing);
        } catch (CloudServiceInteractionException e) {
            throw new AuthenticationException("Failed to verify certificate with cloud", e);
        }
    }

    private Session createGreengrassComponentSession() {
        return new SessionImpl(new Component());
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
