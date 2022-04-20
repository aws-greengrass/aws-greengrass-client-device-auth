/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;

import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.device.iot.Thing;

import java.util.Map;

public class MqttSessionFactory implements SessionFactory {
    private final IotAuthClient iotAuthClient;

    public MqttSessionFactory(IotAuthClient iotAuthClient) {
        this.iotAuthClient = iotAuthClient;
    }

    @Override
    public Session createSession(Map<String, String> credentialMap) throws AuthenticationException {
        // TODO: replace with jackson object mapper
        MqttCredential mqttCredential = new MqttCredential(credentialMap);

        // TODO: support internal client certificates.
        //   Internally issued certificates need to be handled by the entity creating sessions since
        //   we don't yet have the ability to authorize actions for those clients. So, for now, assume
        //   this is an IoT Thing and components will be specially handled elsewhere.
        Thing thing = new Thing(mqttCredential.clientId);
        Certificate cert = new Certificate(mqttCredential.certificatePem);
        if (!iotAuthClient.isThingAttachedToCertificate(thing, cert)) {
            throw new AuthenticationException("unable to authenticate device");
        }

        Session session = new SessionImpl(cert);
        session.putAttributeProvider(Thing.NAMESPACE, thing);
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
