/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;

import java.util.Collections;
import java.util.List;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;

/**
 * Represents the certificateAuthority and ca_type part of the component configuration. Acts as an adapter
 * from the GG Topics to the domain.
 * <p>
 * |---- configuration
 * |    |---- certificateAuthority:
 * |          |---- privateKeyUri: "..."
 * |          |---- certificateUri: "..."
 * |          |---- ca_type: [...]
 * </p>
 */
public class CAConfiguration {
    public static final String CA_CERTIFICATE_URI = "certificateUri";
    public static final String CA_PRIVATE_KEY_URI = "privateKeyUri";
    public static final String CA_TYPE_KEY = "ca_type";
    public static final String CERTIFICATE_AUTHORITY_TOPIC = "certificateAuthority";
    private static final Logger logger = LogManager.getLogger(CAConfiguration.class);
    private final Topics config;

    public CAConfiguration(Topics config) {
        this.config = config;
    }

    private Topics getCAConfigTopics() {
        return config.lookupTopics(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC);
    }

    /**
     * Returns the configuration value for ca_type.
     */
    public List<String> getCaTypeList() {
        Topic caTypeTopic = getCAConfigTopics().lookup(CA_TYPE_KEY);

        if (caTypeTopic.getOnce() == null) {
            return Collections.emptyList();
        }

        return Coerce.toStringList(caTypeTopic.getOnce());
    }

    /**
     * Returns the certificate type based on the ca_types configuration. If it is empty the type will
     * default to RSA_2048, otherwise it will use the first value of that list.
     */
    public CertificateStore.CAType getCaType() {
        List<String> caTypeList = getCaTypeList();
        logger.atDebug().kv("CA type", caTypeList).log("CA type list updated");

        if (caTypeList.isEmpty()) {
            logger.atDebug().log("CA type list null or empty. Defaulting to RSA");
            return  CertificateStore.CAType.RSA_2048;
        }

        if (caTypeList.size() > 1) {
            logger.atWarn().log("Only one CA type is supported. Ignoring subsequent CAs in the list.");
        }

        String caType = caTypeList.get(0);
        return CertificateStore.CAType.valueOf(caType);
    }


    /**
     * Returns the privateKeyUri value from the configuration.
     */
    public String getCaPrivateKeyUri() {
        Topic privateKeyUriTopic = getCAConfigTopics().find(CA_PRIVATE_KEY_URI);
        return Coerce.toString(privateKeyUriTopic);
    }

    /**
     * Returns the certificateUri from the configuration.
     */
    public String getCaCertificateUri() {
        Topic privateKeyUriTopic = getCAConfigTopics().find(CA_CERTIFICATE_URI);
        return Coerce.toString(privateKeyUriTopic);
    }
}
