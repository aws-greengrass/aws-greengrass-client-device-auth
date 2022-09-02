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
import lombok.Getter;

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
@Getter
public final class CAConfiguration {
    public static final String CA_CERTIFICATE_URI = "certificateUri";
    public static final String CA_PRIVATE_KEY_URI = "privateKeyUri";
    public static final String DEPRECATED_CA_TYPE_KEY = "ca_type";
    public static final String CA_TYPE_KEY = "caType";
    public static final String CERTIFICATE_AUTHORITY_TOPIC = "certificateAuthority";
    private static final Logger logger = LogManager.getLogger(CAConfiguration.class);

    private CertificateStore.CAType caType;
    private List<String> caTypeList;
    private String privateKeyUri;
    private String certificateUri;


    private CAConfiguration(List<String> caTypes, CertificateStore.CAType caType,
                            String privateKeyUri, String certificateUri) {
        this.caType = caType;
        this.caTypeList = caTypes;
        this.privateKeyUri = privateKeyUri;
        this.certificateUri = certificateUri;
    }

    /**
     * Factory method for creating an immutable CAConfiguration from the service configuration.
     *
     * @param config the root service configuration
     */
    public static CAConfiguration from(Topics config) {
        Topics configurationTopic = config.lookupTopics(CONFIGURATION_CONFIG_KEY);
        Topics certAuthorityTopic = configurationTopic.lookupTopics(CERTIFICATE_AUTHORITY_TOPIC);

        return new CAConfiguration(
                getCaTypeListFromConfiguration(configurationTopic),
                getCaTypeFromConfiguration(configurationTopic),
                getCaPrivateKeyUriFromConfiguration(certAuthorityTopic),
                getCaCertificateUriFromConfiguration(certAuthorityTopic)
        );
    }


    private static List<String> getCaTypeListFromConfiguration(Topics configurationTopic) {
        // NOTE: This should be a list of CertificateStore.CAType and not any random Strings
        Topic caTypeTopic = configurationTopic.lookup(CERTIFICATE_AUTHORITY_TOPIC, CA_TYPE_KEY);
        if (caTypeTopic.getOnce() != null) {
            return Coerce.toStringList(caTypeTopic.getOnce());
        }

        // NOTE: Ensure backwards compat with v.2.2.2 we are moving the ca_type key to be under
        // certificateAuthority and changing its name to caType. (ONLY REMOVE AFTER IT IS SAFE)
        Topic deprecatedCaTypeTopic = configurationTopic.lookup(DEPRECATED_CA_TYPE_KEY);
        if (deprecatedCaTypeTopic.getOnce() != null) {
            return Coerce.toStringList(deprecatedCaTypeTopic.getOnce());
        }

        return Collections.emptyList();
    }

    private static CertificateStore.CAType getCaTypeFromConfiguration(Topics configurationTopic) {
        List<String> caTypeList = getCaTypeListFromConfiguration(configurationTopic);
        logger.atDebug().kv("CA type", caTypeList).log("CA type list updated");

        if (caTypeList.isEmpty()) {
            logger.atDebug().log("CA type list null or empty. Defaulting to RSA");
            return CertificateStore.CAType.RSA_2048;
        }

        if (caTypeList.size() > 1) {
            logger.atWarn().log("Only one CA type is supported. Ignoring subsequent CAs in the list.");
        }

        String caType = caTypeList.get(0);
        return CertificateStore.CAType.valueOf(caType);
    }

    private static String getCaPrivateKeyUriFromConfiguration(Topics certAuthorityTopic) {
        // NOTE: Parse the key to ensure it is a valid URI
        //  before returning it
        Topic privateKeyUriTopic = certAuthorityTopic.find(CA_PRIVATE_KEY_URI);
        return Coerce.toString(privateKeyUriTopic);
    }

    private static String getCaCertificateUriFromConfiguration(Topics certAuthorityTopic) {
        // NOTE: Parse the key to ensure it is a valid URI
        //  before returning it
        Topic privateKeyUriTopic = certAuthorityTopic.find(CA_CERTIFICATE_URI);
        return Coerce.toString(privateKeyUriTopic);
    }
}