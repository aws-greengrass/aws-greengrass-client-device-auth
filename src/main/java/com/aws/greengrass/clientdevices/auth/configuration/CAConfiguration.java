/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;
import lombok.Getter;

import java.util.List;

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.AUTHORITIES_TOPIC;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CA_PASSPHRASE;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CERTIFICATES_KEY;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.lifecyclemanager.GreengrassService.RUNTIME_STORE_NAMESPACE_TOPIC;

@Getter
@SuppressWarnings("PMD.DataClass")
public class CAConfiguration {
    public static final String CERTIFICATE_AUTHORITY_TOPIC = "certificateAuthority";
    public static final String CA_CERTIFICATE_URI = "certificateUri";
    public static final String CA_PRIVATE_KEY_URI = "privateKeyUri";
    public static final String CA_TYPE_TOPIC = "ca_type";
    private final String caPrivateKeyUri;
    private final String caCertificateUri;
    private final List<String> caTypeList;
    private String caPassphrase;
    private final Topics cdaConfigTopics;

    /**
     * Creates CA configuration object with the latest CDA config.
     *
     * @param cdaConfigTopics CDA service configuration topics
     */
    public CAConfiguration(Topics cdaConfigTopics) {
        this.cdaConfigTopics = cdaConfigTopics;
        Topics certificateAuthorityTopics = cdaConfigTopics.lookupTopics(CONFIGURATION_CONFIG_KEY,
                CERTIFICATE_AUTHORITY_TOPIC);
        caPrivateKeyUri = Coerce.toString(certificateAuthorityTopics.find(CA_PRIVATE_KEY_URI));
        caCertificateUri = Coerce.toString(certificateAuthorityTopics.find(CA_CERTIFICATE_URI));
        caTypeList = Coerce.toStringList(certificateAuthorityTopics.find(CA_TYPE_TOPIC));

        Topics cdaRuntimeTopics = cdaConfigTopics.lookupTopics(RUNTIME_STORE_NAMESPACE_TOPIC);
        caPassphrase = Coerce.toString(cdaRuntimeTopics.lookup(CA_PASSPHRASE).dflt(""));
    }

    public void updateCaPassphraseConfig(String newPassphrase) {
        cdaConfigTopics.lookup(RUNTIME_STORE_NAMESPACE_TOPIC, CA_PASSPHRASE).withValue(newPassphrase);
        caPassphrase = newPassphrase;
    }

    public void updateCaCertificateConfig(List<String> caCertificates) {
        cdaConfigTopics.lookup(RUNTIME_STORE_NAMESPACE_TOPIC, CERTIFICATES_KEY, AUTHORITIES_TOPIC)
                .withValue(caCertificates);
    }
}
