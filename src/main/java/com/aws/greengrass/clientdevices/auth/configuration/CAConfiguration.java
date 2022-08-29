/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;
import lombok.Getter;

import java.util.List;

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CA_PASSPHRASE;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.lifecyclemanager.GreengrassService.RUNTIME_STORE_NAMESPACE_TOPIC;

@Getter
public class CAConfiguration {
    public static final String CERTIFICATE_AUTHORITY_TOPIC = "certificateAuthority";
    public static final String CA_CERTIFICATE_URI = "certificateUri";
    public static final String CA_PRIVATE_KEY_URI = "privateKeyUri";
    public static final String CA_TYPE_TOPIC = "ca_type";
    private String caPrivateKeyUri;
    private String caCertificateUri;
    private List<String> caTypeList;
    private String caPassphrase;
    private final Topics cdaConfigTopics;

    /**
     * Construct CA Configuration object from the CDA component configuration.
     *
     * @param cdaConfigTopics CDA service configuration topics
     */
    public CAConfiguration(Topics cdaConfigTopics) {
        this.cdaConfigTopics = cdaConfigTopics;
        this.updateCAConfiguration();
    }

    /**
     * Updates the CA configuration with the latest CDA config.
     */
    public synchronized void updateCAConfiguration() {
        Topics certificateAuthorityTopics = cdaConfigTopics.lookupTopics(CONFIGURATION_CONFIG_KEY,
                CERTIFICATE_AUTHORITY_TOPIC);
        caPrivateKeyUri = Coerce.toString(certificateAuthorityTopics.find(CA_PRIVATE_KEY_URI));
        caCertificateUri = Coerce.toString(certificateAuthorityTopics.find(CA_CERTIFICATE_URI));
        caTypeList = Coerce.toStringList(certificateAuthorityTopics.find(CA_TYPE_TOPIC));

        Topics cdaRuntimeTopics = cdaConfigTopics.lookupTopics(RUNTIME_STORE_NAMESPACE_TOPIC);
        caPassphrase = Coerce.toString(cdaRuntimeTopics.find(CA_PASSPHRASE));
    }
}
