/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.events.CAConfigurationChanged;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.config.Topics;
import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
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
    @Getter
    private final CAConfiguration caConfig;
    private final DomainEvents domainEvents;

    private CDAConfiguration(DomainEvents domainEvents, RuntimeConfiguration runtime, CAConfiguration ca) {
        this.domainEvents = domainEvents;
        this.runtime = runtime;
        this.caConfig = ca;
    }

    /**
     * Creates the CDA (Client Device Auth) Service configuration.
     *
     * @param existingConfig  an existing version of the CDAConfiguration
     * @param topics configuration topics from GG
     *
     * @throws URISyntaxException if invalid URI inside the configuration
     * @throws InvalidConfigurationException if a part of the configuration is invalid
     */
    public static CDAConfiguration from(CDAConfiguration existingConfig, Topics topics) throws URISyntaxException,
            InvalidConfigurationException {
        Topics runtimeTopics = topics.lookupTopics(RUNTIME_STORE_NAMESPACE_TOPIC);
        Topics serviceConfiguration = topics.lookupTopics(CONFIGURATION_CONFIG_KEY);

        DomainEvents domainEvents = topics.getContext().get(DomainEvents.class);

        CDAConfiguration newConfig = new CDAConfiguration(
            domainEvents,
            RuntimeConfiguration.from(runtimeTopics),
            CAConfiguration.from(serviceConfiguration)
        );

        newConfig.triggerChanges(newConfig, existingConfig);

        return newConfig;
    }

    /**
     * Creates the CDA (Client Device Auth) Service configuration.
     *
     * @param topics configuration topics from GG
     *
     * @throws URISyntaxException if invalid URI inside the configuration
     * @throws InvalidConfigurationException if a part of the configuration is invalid
     */
    public static CDAConfiguration from(Topics topics) throws URISyntaxException, InvalidConfigurationException {
        return from(null, topics);
    }

    private void triggerChanges(CDAConfiguration current, CDAConfiguration prev) {
        if (prev == null || !caConfig.isEqual(prev.getCaConfig())) {
            domainEvents.emit(new CAConfigurationChanged(current));
        }
    }

    public boolean isUsingCustomCA() {
        return caConfig.isUsingCustomCA();
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
        return caConfig.getCaType();
    }

    public Optional<URI> getPrivateKeyUri() {
        return caConfig.getPrivateKeyUri();
    }

    public Optional<URI> getCertificateUri() {
        return caConfig.getCertificateUri();
    }

    /**
     * Verifies if the configuration for the certificateAuthority is equal to another CDA configuration.
     *
     * @param configuration  CDAConfiguration
     */
    public boolean isEqual(CDAConfiguration configuration) {
        if (configuration == null) {
            return false;
        }

        // TODO: As we add more configurations here we should change the equality comparison.
        return caConfig.isEqual(configuration.getCaConfig());
    }
}
