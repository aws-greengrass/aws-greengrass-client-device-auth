/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager;

import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.certificatemanager.model.DeviceConfig;
import com.aws.greengrass.config.Node;
import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.config.WhatHappened;
import com.aws.greengrass.dependency.ImplementsService;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.lifecyclemanager.PluginService;
import com.aws.greengrass.util.Coerce;
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

@ImplementsService(name = DCMService.DCM_SERVICE_NAME)
public class DCMService extends PluginService {
    public static final String DCM_SERVICE_NAME = "aws.greengrass.CertificateManager";
    private static final JsonMapper OBJECT_MAPPER =
            JsonMapper.builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true).build();

    /**
     * Certificate Manager Service uses the following topic structure:
     *  |---- config
     *  |    |---- devices: [...]
     *  |    |---- runtime
     *  |        |---- certificates
     *  |            |---- authorities: [...]
     *  |            |---- devices: [...]
     *  |        |---- ca_passphrase: "..."
     */
    public static final String CERTIFICATES_KEY = "certificates";
    public static final String AUTHORITIES_TOPIC = "authorities";
    public static final String DEVICES_TOPIC = "devices";
    public static final String CA_PASSPHRASE = "ca_passphrase";

    private final CertificateManager certificateManager;

    /**
     * Constructor.
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
        this.config.lookup(DEVICES_TOPIC)
                .subscribe(this::onConfigChange);
    }

    public CertificateManager getCertificateManager() {
        return this.certificateManager;
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void onConfigChange(WhatHappened what, Node node) {
        Topic devices = this.config.lookup(DEVICES_TOPIC).dflt("[]");
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
        try {
            updateDeviceCertificateConfig(certificateManager.getDeviceCertificates());
        } catch (JsonProcessingException e) {
            logger.atError().cause(e).log("unable to update device configuration");
            serviceErrored(e);
        }
    }

    void updateDeviceCertificateConfig(Map<String, String> clientCerts) throws JsonProcessingException {
        Topic clientCertsTopic = getRuntimeConfig().lookup(CERTIFICATES_KEY, DEVICES_TOPIC);
        clientCertsTopic.withValue(OBJECT_MAPPER.writeValueAsString(clientCerts));
    }

    void updateCACertificateConfig(List<String> caCerts) {
        Topic caCertsTopic = getRuntimeConfig().lookup(CERTIFICATES_KEY, AUTHORITIES_TOPIC);
        caCertsTopic.withValue(caCerts);
    }

    @Override
    public void startup() {
        try {
            certificateManager.init(getPassphrase());
            List<String> caCerts = certificateManager.getCACertificates();
            updateCACertificateConfig(caCerts);
            reportState(State.RUNNING);
        } catch (KeyStoreException | IOException | CertificateEncodingException e) {
            serviceErrored(e);
        }
    }

    private String getPassphrase() {
        // TODO: This passphrase needs to be encrypted prior to storing in TLOG
        Topic caPassphrase = getRuntimeConfig().lookup(CA_PASSPHRASE)
                .dflt(CertificateStore.generateRandomPassphrase());
        return Coerce.toString(caPassphrase);
    }
}
