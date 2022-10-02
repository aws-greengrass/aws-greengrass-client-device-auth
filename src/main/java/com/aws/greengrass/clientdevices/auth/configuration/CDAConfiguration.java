/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.events.CAConfigurationChanged;
import com.aws.greengrass.config.Topics;
import lombok.Getter;

import java.net.URI;
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
    @Getter
    private final RuntimeConfiguration runtimeConfig;
    @Getter
    public final CAConfiguration caConfig;
    private final Topics configurationTopics;

    private CDAConfiguration(Topics configurationTopics, RuntimeConfiguration runtimeConfig, CAConfiguration caConfig) {
        this.configurationTopics = configurationTopics;
        this.runtimeConfig = runtimeConfig;
        this.caConfig = caConfig;
    }

    /**
     * Creates the CDA (Client Device Auth) Service configuration. And allows it to be available in the context
     * with the updated values
     *
     * @param existing  an existing version of the CDAConfiguration
     * @param topics configuration topics from GG
     */
    public static Result<CDAConfiguration> from(CDAConfiguration existing, Topics topics) {
        Result<CDAConfiguration> cdaConfigResult = getInstance(topics);
        cdaConfigResult.ifOk((config) -> config.triggerChanges(existing));
        return cdaConfigResult;
    }

    /**
     * Creates the CDA (Client Device Auth) Service configuration.
     *
     * @param topics configuration topics from GG
     */
    public static Result<CDAConfiguration> from(Topics topics) {
        return from(null, topics);
    }

    private static Result<CDAConfiguration> getInstance(Topics configurationTopics) {
        RuntimeConfiguration runtimeConfig =  RuntimeConfiguration.from(
                configurationTopics.lookupTopics(RUNTIME_STORE_NAMESPACE_TOPIC));
        Result<CAConfiguration> caConfigResult =  CAConfiguration.from(
                configurationTopics.lookupTopics(CONFIGURATION_CONFIG_KEY));

        CDAConfiguration cdaConfiguration = new CDAConfiguration(
                configurationTopics, runtimeConfig, caConfigResult.get());

        if (caConfigResult.isError()) {
            return Result.error(cdaConfiguration, caConfigResult.getError());
        }

        return Result.ok(cdaConfiguration);
    }

    private void triggerChanges(CDAConfiguration prev) {
        DomainEvents eventEmitter = configurationTopics.getContext().get(DomainEvents.class);

        if (prev == null || !caConfig.isEqual(prev.getCaConfig())) {
            eventEmitter.emit(new CAConfigurationChanged(this));
        }
    }

    public boolean isUsingCustomCA() {
      return caConfig.isUsingCustomCA();
    }

    public String getCaPassphrase() {
        return runtimeConfig.getCaPassphrase();
    }

    public void updateCAPassphrase(String caPassPhrase) {
        runtimeConfig.updateCAPassphrase(caPassPhrase);
    }

    public void updateCACertificates(List<String> caCertificates) {
        runtimeConfig.updateCACertificates(caCertificates);
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
     * Checks if the configuration has changed compared to another one.
     * @param configuration An instance of the CDAConfiguration
     */
    public boolean isEqual(CDAConfiguration configuration) {
        if (configuration == null) {
            return false;
        }

        // TODO: As we add more configurations here we should change the equality comparison.
        return caConfig.isEqual(configuration.getCaConfig());
    }
}
