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
import com.aws.greengrass.lifecyclemanager.PluginService;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.Utils;
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

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;

@SuppressWarnings("PMD.DataClass")
@ImplementsService(name = DCMService.DCM_SERVICE_NAME)
public class DCMService extends PluginService {
    public static final String DCM_SERVICE_NAME = "aws.greengrass.CertificateManager";
    private static final JsonMapper OBJECT_MAPPER =
            JsonMapper.builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true).build();

    /**
     * Certificate Manager Service uses the following topic structure:
     *  |---- configuration
     *  |    |---- devices: [...]
     *  |    |---- ca_type: [...]
     *  |---- runtime
     *  |    |---- ca_passphrase: "..."
     *  |    |---- certificates
     *  |         |---- authorities: [...]
     *  |         |---- devices: [...]
     */
    public static final String CERTIFICATES_KEY = "certificates";
    public static final String AUTHORITIES_TOPIC = "authorities";
    public static final String DEVICES_TOPIC = "devices";
    public static final String CA_PASSPHRASE = "ca_passphrase";
    public static final String CA_TYPE = "ca_type";

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
        this.config.lookup(CONFIGURATION_CONFIG_KEY, DEVICES_TOPIC)
                .subscribe(this::onConfigChange);
        this.config.lookup(CONFIGURATION_CONFIG_KEY, CA_TYPE).subscribe(this::updateCAType);
    }

    public CertificateManager getCertificateManager() {
        return this.certificateManager;
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void onConfigChange(WhatHappened what, Node node) {
        Topic devices = this.config.lookup(CONFIGURATION_CONFIG_KEY, DEVICES_TOPIC).dflt("[]");
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

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void updateCAType(WhatHappened what, Topic topic) {
        try {
            List<String> caTypeList = Coerce.toStringList(topic);
            logger.atDebug().kv("CA type", caTypeList).log("CA type list updated");

            if (Utils.isEmpty(caTypeList)) {
                logger.atDebug().log("CA type list null or empty. Defaulting to RSA");
                certificateManager.update(getPassphrase(), CertificateStore.CAType.RSA_2048);
            } else {
                if (caTypeList.size() > 1) {
                    logger.atWarn().log("Only one CA type is supported. Ignoring subsequent CAs in the list.");
                }
                String caType = caTypeList.get(0);
                certificateManager.update(getPassphrase(), CertificateStore.CAType.valueOf(caType));
            }

            List<String> caCerts = certificateManager.getCACertificates();
            updateCACertificateConfig(caCerts);
        } catch (KeyStoreException | IOException | CertificateEncodingException | IllegalArgumentException e) {
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

    private String getPassphrase() {
        // TODO: This passphrase needs to be encrypted prior to storing in TLOG
        Topic caPassphrase = getRuntimeConfig().lookup(CA_PASSPHRASE)
                .dflt(CertificateStore.generateRandomPassphrase());
        return Coerce.toString(caPassphrase);
    }
}
