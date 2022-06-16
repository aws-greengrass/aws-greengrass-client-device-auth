/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;

import com.aws.greengrass.device.DeviceAuthClient;
import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.CertificateRegistry;
import com.aws.greengrass.device.iot.Component;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.device.iot.Thing;

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

public class MqttSessionFactory implements SessionFactory {
    private final IotAuthClient iotAuthClient;
    private final DeviceAuthClient deviceAuthClient;
    private final CertificateRegistry certificateRegistry;

    /**
     * Constructor.
     *
     * @param iotAuthClient       Iot auth client
     * @param deviceAuthClient    Device auth client
     * @param certificateRegistry device Certificate registry
     */
    @Inject
    public MqttSessionFactory(IotAuthClient iotAuthClient,
                              DeviceAuthClient deviceAuthClient,
                              CertificateRegistry certificateRegistry) {
        this.iotAuthClient = iotAuthClient;
        this.deviceAuthClient = deviceAuthClient;
        this.certificateRegistry = certificateRegistry;
    }

    @Override
    public Session createSession(Map<String, String> credentialMap) throws AuthenticationException {
        // TODO: replace with jackson object mapper
        MqttCredential mqttCredential = new MqttCredential(credentialMap);

        boolean isGreengrassComponent = deviceAuthClient.isGreengrassComponent(mqttCredential.certificatePem);
        if (isGreengrassComponent) {
            return createGreengrassComponentSession(mqttCredential);
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
            Thing thing = new Thing(mqttCredential.clientId);
            Certificate cert = new Certificate(certificateId.get());
            if (!iotAuthClient.isThingAttachedToCertificate(thing, cert)) {
                throw new AuthenticationException("unable to authenticate device");
            }
            Session session = new SessionImpl(cert);
            session.putAttributeProvider(Thing.NAMESPACE, thing);
            return session;
        } catch (CloudServiceInteractionException e) {
            throw new AuthenticationException("Failed to verify certificate with cloud", e);
        }
    }

    private Session createGreengrassComponentSession(MqttCredential mqttCredential) {
        Certificate cert = new Certificate(mqttCredential.clientId);
        Session session = new SessionImpl(cert);
        session.putAttributeProvider(Component.NAMESPACE, new Component());
        return session;
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
