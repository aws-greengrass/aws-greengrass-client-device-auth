/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.certificatemanager;

import com.aws.iot.evergreen.certificatemanager.model.DeviceConfig;
import com.aws.iot.evergreen.config.Node;
import com.aws.iot.evergreen.config.Topic;
import com.aws.iot.evergreen.config.Topics;
import com.aws.iot.evergreen.config.WhatHappened;
import com.aws.iot.evergreen.dependency.ImplementsService;
import com.aws.iot.evergreen.dependency.State;
import com.aws.iot.evergreen.kernel.EvergreenService;
import com.aws.iot.evergreen.util.Coerce;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

import static com.aws.iot.evergreen.packagemanager.KernelConfigResolver.PARAMETERS_CONFIG_KEY;

@ImplementsService(name = DCMService.DCM_SERVICE_NAME, autostart = true)
@Singleton
public class DCMService extends EvergreenService {
    public static final String DCM_SERVICE_NAME = "aws.greengrass.CertificateManager";
    private static final JsonMapper OBJECT_MAPPER =
            JsonMapper.builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true).build();

    /**
     * Certificate Manager Service uses the following topic structure:
     *  |---- parameters
     *  |    |---- devices: [...]
     *  |---- runtime
     *  |    |---- certificates
     *  |        |---- authorities: [...]
     *  |        |---- devices: [...]
     */
    public static final String CERTIFICATES_KEY = "certificates";
    public static final String AUTHORITIES_TOPIC = "authorities";
    public static final String DEVICES_TOPIC = "devices";

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

    public CertificateManager getCertificateManager() {
        return this.certificateManager;
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

        // Download any missing device certificates
        if (deviceConfigList == null) {
            deviceConfigList = new ArrayList<>();
        }
        certificateManager.setDeviceConfigurations(deviceConfigList);
        updateDeviceCertificateConfig(certificateManager.getDeviceCertificates());
    }

    void updateDeviceCertificateConfig(Map<String, String> clientCerts) {
        Topic clientCertsTopic = this.config.lookup(RUNTIME_STORE_NAMESPACE_TOPIC, CERTIFICATES_KEY, DEVICES_TOPIC);
        clientCertsTopic.withValue(clientCerts);
    }

    void updateCACertificateConfig(List<String> caCerts) {
        Topic caCertsTopic = this.config.lookup(RUNTIME_STORE_NAMESPACE_TOPIC, CERTIFICATES_KEY, AUTHORITIES_TOPIC);
        caCertsTopic.withValue(caCerts);
    }

    @Override
    public void startup() {
        try {
            certificateManager.initialize();
            List<String> caCerts = certificateManager.getCACertificates();
            updateCACertificateConfig(caCerts);
            for (String ca : caCerts) {
                logger.atInfo().kv("ca", ca).log("CA certificate");
            }
            reportState(State.RUNNING);
        } catch (KeyStoreException | IOException | CertificateEncodingException e) {
            serviceErrored(e);
        }
    }
}
