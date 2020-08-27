/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.dcm;

import com.aws.iot.evergreen.config.Node;
import com.aws.iot.evergreen.config.Topic;
import com.aws.iot.evergreen.config.Topics;
import com.aws.iot.evergreen.config.WhatHappened;
import com.aws.iot.evergreen.dcm.certificate.CertificateManager;
import com.aws.iot.evergreen.dcm.model.DeviceConfig;
import com.aws.iot.evergreen.dependency.ImplementsService;
import com.aws.iot.evergreen.dependency.State;
import com.aws.iot.evergreen.kernel.EvergreenService;
import com.aws.iot.evergreen.util.Coerce;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import static com.aws.iot.evergreen.packagemanager.KernelConfigResolver.PARAMETERS_CONFIG_KEY;

@ImplementsService(name = DCMService.DCM_SERVICE_NAME, autostart = true)
@Singleton
public class DCMService extends EvergreenService {
    public static final String DCM_SERVICE_NAME = "aws.greengrass.certificate.manager";
    private static final String DEVICES_TOPIC = "devices";
    private static final JsonMapper OBJECT_MAPPER =
            JsonMapper.builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true).build();

    private final CertificateManager certificateManager;

    /**
     * Constructor for EvergreenService.
     *
     * @param topics             Root Configuration topic for this service
     * @param certificateManager Certificate manager
     */
    @Inject
    public DCMService(Topics topics, CertificateManager certificateManager) {
        super(topics);
        this.certificateManager = certificateManager;
    }

    @Override
    protected void install() throws InterruptedException {
        super.install();
        this.config.lookup(PARAMETERS_CONFIG_KEY, DEVICES_TOPIC)
                .subscribe(this::onConfigChange);
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void onConfigChange(WhatHappened what, Node node) {
        Topic devices = this.config.lookup(PARAMETERS_CONFIG_KEY, DEVICES_TOPIC).dflt("[]");
        List<DeviceConfig> deviceConfigList = null;
        String val = Coerce.toString(devices);

        if (val != null) {
            try {
                deviceConfigList = OBJECT_MAPPER.readValue(val, new TypeReference<List<DeviceConfig>>() {});
            } catch (JsonProcessingException e) {
                logger.atError().kv("node", devices.getFullName()).kv("value", val)
                        .log("Malformed device configuration", e);
            }
        }

        if (deviceConfigList == null) {
            deviceConfigList = new ArrayList<>();
        }
        certificateManager.setDeviceConfigurations(deviceConfigList);
    }

    @Override
    public void startup() {
        try {
            certificateManager.initialize();
            reportState(State.RUNNING);
        } catch (KeyStoreException e) {
            serviceErrored(e);
        }
    }
}
