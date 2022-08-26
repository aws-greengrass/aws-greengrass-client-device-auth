/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;

import java.util.List;

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CA_CERTIFICATE_URI;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CA_PASSPHRASE;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CA_PRIVATE_KEY_URI;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CA_TYPE_TOPIC;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CERTIFICATE_AUTHORITY_TOPIC;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.lifecyclemanager.GreengrassService.RUNTIME_STORE_NAMESPACE_TOPIC;

public class CAConfiguration {
    private final Topics certificateAuthorityTopics;
    private final Topics cdaRuntimeTopics;

    /**
     * Construct CA Configuration object from the CDA component configuration.
     *
     * @param cdaConfigTopics CDA service configuration topics
     */
    public CAConfiguration(Topics cdaConfigTopics) {
        this.certificateAuthorityTopics = cdaConfigTopics.lookupTopics(CONFIGURATION_CONFIG_KEY,
                CERTIFICATE_AUTHORITY_TOPIC);
        this.cdaRuntimeTopics = cdaConfigTopics.lookupTopics(RUNTIME_STORE_NAMESPACE_TOPIC);
    }

    public List<String> getCATypeList() {
        return Coerce.toStringList(certificateAuthorityTopics.find(CA_TYPE_TOPIC));
    }

    public String getCakeyUri() {
        return Coerce.toString(certificateAuthorityTopics.find(CA_PRIVATE_KEY_URI));
    }

    public String getCaCertUri() {
        return Coerce.toString(certificateAuthorityTopics.find(CA_CERTIFICATE_URI));
    }


    /**
     * Update the passphrase of the keystore CDA runtime configuration.
     *
     * @param passphrase   Passphrase used for KeyStore and private key entries.
     */
    public void updateCaPassphraseConfig(String passphrase) {
        Topic caPassphrase = cdaRuntimeTopics.lookup(CA_PASSPHRASE);
        // TODO: This passphrase needs to be encrypted prior to storing in TLOG
        caPassphrase.withValue(passphrase);
    }

    public String getCaPassphraseFromConfig() {
        Topic caPassphrase = cdaRuntimeTopics.lookup(CA_PASSPHRASE).dflt("");
        return Coerce.toString(caPassphrase);
    }

}
