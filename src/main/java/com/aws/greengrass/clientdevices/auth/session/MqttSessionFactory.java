/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.DeviceAuthClient;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.iot.Component;
import com.aws.greengrass.clientdevices.auth.iot.dto.CreateSessionDTO;
import com.aws.greengrass.clientdevices.auth.iot.usecases.CreateIoTThingSession;

import java.util.Map;
import javax.inject.Inject;

public class MqttSessionFactory implements SessionFactory {
    private final DeviceAuthClient deviceAuthClient;
    private final UseCases useCases;

    /**
     * Constructor.
     *
     * @param deviceAuthClient Device auth client
     * @param useCases         useCases
     */
    @Inject
    public MqttSessionFactory(DeviceAuthClient deviceAuthClient, UseCases useCases) {
        this.deviceAuthClient = deviceAuthClient;
        this.useCases = useCases;
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
        // NOTE: We should remove calling this useCase from here, but for now it serves its purpose. We will
        //  refactor this later
        CreateIoTThingSession useCase = useCases.get(CreateIoTThingSession.class);
        CreateSessionDTO command = new CreateSessionDTO(mqttCredential.clientId, mqttCredential.certificatePem);
        return useCase.apply(command);
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
