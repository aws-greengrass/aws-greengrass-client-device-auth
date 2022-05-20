/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;

import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.device.iot.Thing;
import com.aws.greengrass.device.session.credentials.Credential;
import com.aws.greengrass.device.session.credentials.MqttCredential;

import javax.inject.Inject;

public class MqttSessionFactory implements SessionFactory {
    private final IotAuthClient iotAuthClient;

    @Inject
    public MqttSessionFactory(IotAuthClient iotAuthClient) {
        this.iotAuthClient = iotAuthClient;
    }

    @Override
    public Session createSession(Credential credential) throws AuthenticationException {
        if (!credential.getClass().equals(MqttCredential.class)) {
            throw new IllegalArgumentException("Unable to parse MQTT credentials");
        }
        MqttCredential mqttCredential = (MqttCredential) credential;

        // TODO: support internal client certificates.
        //   Internally issued certificates need to be handled by the entity creating sessions since
        //   we don't yet have the ability to authorize actions for those clients. So, for now, assume
        //   this is an IoT Thing and components will be specially handled elsewhere.
        Thing thing = new Thing(mqttCredential.getClientId());
        Certificate cert = new Certificate(mqttCredential.getCertificatePem());
        if (!iotAuthClient.isThingAttachedToCertificate(thing, cert)) {
            throw new AuthenticationException("unable to authenticate device");
        }

        Session session = new SessionImpl(cert);
        session.putAttributeProvider(Thing.NAMESPACE, thing);
        return session;
    }
}
