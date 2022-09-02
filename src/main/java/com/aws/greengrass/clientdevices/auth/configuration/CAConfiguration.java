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
 * |          |---- caType: [...]
 * </p>
 */
public class CAConfiguration {
    public static final String CA_CERTIFICATE_URI = "certificateUri";
    public static final String CA_PRIVATE_KEY_URI = "privateKeyUri";
    public static final String DEPRECATED_CA_TYPE_KEY = "ca_type";
    public static final String CA_TYPE_KEY = "caType";
    public static final String CERTIFICATE_AUTHORITY_TOPIC = "certificateAuthority";
    private static final Logger logger = LogManager.getLogger(CAConfiguration.class);
    private final Topics config;

    /**
     * Update the CA configuration with the latest CDA config.
     *
     * @param config CA configuration object
     */
    public CAConfiguration(Topics config) {
        // NOTE: Validate topics schema upon initialization. Better to fail fast if there is
        // something wrong
        this.config = config;
    }

    private Topics getCAConfigTopics() {
        return config.lookupTopics(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC);
    }

    /**
     * Returns the configuration value for ca_type.
     */
    public List<String> getCaTypeList() {
        // NOTE: This should be a list of CertificateStore.CAType and not any random Strings
        Topic caTypeTopic = getCAConfigTopics().lookup(CA_TYPE_KEY);
        if (caTypeTopic.getOnce() != null) {
            return Coerce.toStringList(caTypeTopic.getOnce());
        }

        // NOTE: Ensure backwards compat with v.2.2.2 we are moving the ca_type key to be under
        // certificateAuthority and changing its name to caType. (ONLY REMOVE AFTER IT IS SAFE)
        Topic deprecatedCaTypeTopic = config.lookup(CONFIGURATION_CONFIG_KEY, DEPRECATED_CA_TYPE_KEY);
        if (deprecatedCaTypeTopic.getOnce() != null) {
            return Coerce.toStringList(deprecatedCaTypeTopic.getOnce());
        }

        return Collections.emptyList();
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
        // NOTE: Parse the key to ensure it is a valid URI
        //  before returning it
        Topic privateKeyUriTopic = getCAConfigTopics().find(CA_PRIVATE_KEY_URI);
        return Coerce.toString(privateKeyUriTopic);
    }

    /**
     * Returns the certificateUri from the configuration.
     */
    public String getCaCertificateUri() {
        // NOTE: Parse the key to ensure it is a valid URI
        //  before returning it
        Topic privateKeyUriTopic = getCAConfigTopics().find(CA_CERTIFICATE_URI);
        return Coerce.toString(privateKeyUriTopic);
    }
}