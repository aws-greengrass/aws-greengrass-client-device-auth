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

import java.util.List;

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
    private final DomainEvents domainEvents;
    @Getter
    public final CAConfiguration caConfig;

    private CDAConfiguration(DomainEvents domainEvents, RuntimeConfiguration runtime, CAConfiguration ca) {
        this.domainEvents = domainEvents;
        this.runtime = runtime;
        this.caConfig = ca;
    }

    /**
     * Creates the CDA (Client Device Auth) Service configuration. And allows it to be available in the context
     * with the updated values
     *
     * @param existingConfig  an existing version of the CDAConfiguration
     * @param topics configuration topics from GG
     */
    public static Result<CDAConfiguration> from(CDAConfiguration existingConfig, Topics topics) {
        Topics serviceConfiguration = topics.lookupTopics(CONFIGURATION_CONFIG_KEY);
        Result<CAConfiguration> caConfigResult =  CAConfiguration.from(serviceConfiguration);

        if (caConfigResult.isError()) {
            return Result.error(caConfigResult.getError());
        }

        DomainEvents domainEvents = topics.getContext().get(DomainEvents.class);
        Topics runtimeTopics = topics.lookupTopics(RUNTIME_STORE_NAMESPACE_TOPIC);

        CDAConfiguration newConfig = new CDAConfiguration(
            domainEvents,
            RuntimeConfiguration.from(runtimeTopics),
            caConfigResult.get()
        );

        newConfig.triggerChanges(existingConfig);
        return Result.ok(newConfig);
    }

    /**
     * Creates the CDA (Client Device Auth) Service configuration.
     *
     * @param topics configuration topics from GG
     */
    public static Result<CDAConfiguration> from(Topics topics) {
        return from(null, topics);
    }

    private void triggerChanges(CDAConfiguration prev) {
        if (hasCAConfigurationChanged(prev)) {
            domainEvents.emit(new CAConfigurationChanged(this));
        }
    }



    public boolean isUsingCustomCA() {
      return getCaConfig().isUsingCustomCA();
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

    /**
     * Verifies if the configuration for the certificateAuthority has changed, given a previous
     * configuration.
     *
     * @param config  CDAConfiguration
     */
    private boolean hasCAConfigurationChanged(CDAConfiguration config) {
        if (config == null) {
            return true;
        }

        return config.getCaConfig().hasChanged(this.getCaConfig());
    }
}
