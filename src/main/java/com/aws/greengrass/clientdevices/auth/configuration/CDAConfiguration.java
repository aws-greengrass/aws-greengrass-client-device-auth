/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.config.Topics;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.lifecyclemanager.GreengrassService.RUNTIME_STORE_NAMESPACE_TOPIC;

/**
 * Aggregate of configuration components. It represents the configuration of the service
 * at a point in time.
 * <p>
 * Certificate Manager Service uses the following topic structure:
 * |---- configuration
 * |    |---- performance:
 * |         |---- cloudRequestQueueSize: "..."
 * |         |---- maxConcurrentCloudRequests: "..."
 * |         |---- maxActiveAuthTokens: "..."
 * |    |---- deviceGroups:
 * |         |---- definitions : {}
 * |         |---- policies : {}
 * |    |---- certificateAuthority:
 * |         |---- privateKeyUri: "..."
 * |         |---- certificateUri: "..."
 * |         |---- caType: [...]
 * |    |---- certificates: {}
 * |---- runtime
 * |    |---- ca_passphrase: "..."
 * |    |---- certificates:
 * |         |---- authorities: [...]
 * |
 * </p>
 */
public final class CDAConfiguration {

    private final RuntimeConfiguration runtime;
    private final CAConfiguration ca;

    private CDAConfiguration(RuntimeConfiguration runtime, CAConfiguration ca) {
       this.runtime = runtime;
       this.ca = ca;
    }

    /**
     * Creates the CDA (Client Device Auth) Service configuration.
     *
     * @param topics configuration topics from GG
     * @throws URISyntaxException if invalid URI inside the configuration
     */
    public static CDAConfiguration from(Topics topics) throws URISyntaxException {
        Topics runtimeTopics = topics.lookupTopics(RUNTIME_STORE_NAMESPACE_TOPIC);
        Topics serviceConfiguration = topics.lookupTopics(CONFIGURATION_CONFIG_KEY);

        return new CDAConfiguration(
            RuntimeConfiguration.from(runtimeTopics),
            CAConfiguration.from(serviceConfiguration)
        );
    }

    public boolean isUsingCustomCA() {
        return ca.isUsingCustomCA();
    }

    public boolean isCaTypesProvided() {
        return ca.getCaTypeList().size() > 0;
    }

    public String getCaPassphrase() {
        return runtime.getCaPassphrase();
    }

    public void updateCAPassphrase(String caPassPhrase) {
        runtime.updateCAPassphrase(caPassPhrase);
    }

    public void updateCACertificates(List<String> caCertificates) {
        runtime.updateCACertificates(caCertificates);
    }

    public CertificateStore.CAType getCaType() {
        return ca.getCaType();
    }

    public Optional<URI> getPrivateKeyUri() {
        return ca.getPrivateKeyUri();
    }

    public Optional<URI> getCertificateUri() {
        return ca.getCertificateUri();
    }

    /**
     * Verifies if the configuration for the certificateAuthority has changed, given a previous
     * configuration.
     *
     * @param config  CDAConfiguration
     */
    public boolean certificateAuthorityChanged(CDAConfiguration config) {
        if (config == null) {
            return true;
        }

        return !Objects.equals(config.getCertificateUri(), getCertificateUri())
                || !Objects.equals(config.getPrivateKeyUri(), getPrivateKeyUri())
                || !Objects.equals(config.getCaType(), getCaType());
    }
}
