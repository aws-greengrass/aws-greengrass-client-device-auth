/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate;

import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;

public class CertificatesConfig {
    private static final Logger LOGGER = LogManager.getLogger(CertificatesConfig.class);

    static final int MAX_SERVER_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 10; // 10 days
    static final int MIN_SERVER_CERT_EXPIRY_SECONDS = 60; // 1 minute
    static final int MAX_CLIENT_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 10; // 10 days
    static final int MIN_CLIENT_CERT_EXPIRY_SECONDS = 60; // 1 minute
    static final int DEFAULT_SERVER_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 7; // 7 days
    static final int DEFAULT_CLIENT_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 7; // 7 days
    static final int DEFAULT_CLUSTER_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 365 * 10; // 10 years
    static final boolean DEFAULT_DISABLE_CERTIFICATE_ROTATION = false;

    private static final String CERTIFICATES_CONFIGURATION = "certificates";
    private static final String SERVER_CERT_VALIDITY_SECONDS = "serverCertificateValiditySeconds";
    private static final String CLIENT_CERT_VALIDITY_SECONDS = "clientCertificateValiditySeconds";
    private static final String DISABLE_CERTIFICATE_ROTATION = "disableCertificateRotation";

    static final String[] PATH_SERVER_CERT_EXPIRY_SECONDS =
            {KernelConfigResolver.CONFIGURATION_CONFIG_KEY, CERTIFICATES_CONFIGURATION, SERVER_CERT_VALIDITY_SECONDS};
    static final String[] PATH_CLIENT_CERT_EXPIRY_SECONDS =
            {KernelConfigResolver.CONFIGURATION_CONFIG_KEY, CERTIFICATES_CONFIGURATION, CLIENT_CERT_VALIDITY_SECONDS};
    static final String[] PATH_DISABLE_CERTIFICATE_ROTATION =
            {KernelConfigResolver.CONFIGURATION_CONFIG_KEY, CERTIFICATES_CONFIGURATION, DISABLE_CERTIFICATE_ROTATION};

    private final Topics configuration;

    public CertificatesConfig(Topics configuration) {
        this.configuration = configuration;
    }

    /**
     * Get server certificate validity period.
     *
     * @return Server certificate validity in seconds
     */
    public int getServerCertValiditySeconds() {
        int configuredValidityPeriod = Coerce.toInt(
                configuration.findOrDefault(DEFAULT_SERVER_CERT_EXPIRY_SECONDS, PATH_SERVER_CERT_EXPIRY_SECONDS));
        if (configuredValidityPeriod > MAX_SERVER_CERT_EXPIRY_SECONDS) {
            LOGGER.atWarn().kv(SERVER_CERT_VALIDITY_SECONDS, configuredValidityPeriod)
                    .kv("maxAllowable", MAX_SERVER_CERT_EXPIRY_SECONDS)
                    .log("Using maximum allowable duration for server certificate validity period");
            return MAX_SERVER_CERT_EXPIRY_SECONDS;
        } else if (configuredValidityPeriod < MIN_SERVER_CERT_EXPIRY_SECONDS) {
            LOGGER.atWarn().kv(SERVER_CERT_VALIDITY_SECONDS, configuredValidityPeriod)
                    .kv("minAllowable", MIN_SERVER_CERT_EXPIRY_SECONDS)
                    .log("Using minimum allowable duration for server certificate validity period");
            return MIN_SERVER_CERT_EXPIRY_SECONDS;
        }
        return configuredValidityPeriod;
    }

    /**
     * Get cluster certificate validity period.
     *
     * @return Cluster certificate validity in seconds
     */
    public int getClusterCertValiditySeconds() {
        // TODO: Make cluster validity period configurable.
        return DEFAULT_CLUSTER_CERT_EXPIRY_SECONDS;
    }

    /**
     * Get client certificate validity period.
     *
     * @return Client certificate validity in seconds
     */
    public int getClientCertValiditySeconds() {
        int configuredValidityPeriod = Coerce.toInt(configuration.findOrDefault(DEFAULT_CLIENT_CERT_EXPIRY_SECONDS,
                PATH_CLIENT_CERT_EXPIRY_SECONDS));
        if (configuredValidityPeriod > MAX_CLIENT_CERT_EXPIRY_SECONDS) {
            LOGGER.atWarn().kv(CLIENT_CERT_VALIDITY_SECONDS, configuredValidityPeriod)
                    .kv("maxAllowable", MAX_CLIENT_CERT_EXPIRY_SECONDS)
                    .log("Using maximum allowable duration for client certificate validity period");
            return MAX_CLIENT_CERT_EXPIRY_SECONDS;
        } else if (configuredValidityPeriod < MIN_CLIENT_CERT_EXPIRY_SECONDS) {
            LOGGER.atWarn().kv(CLIENT_CERT_VALIDITY_SECONDS, configuredValidityPeriod)
                    .kv("minAllowable", MAX_CLIENT_CERT_EXPIRY_SECONDS)
                    .log("Using minimum allowable duration for client certificate validity period");
            return MIN_CLIENT_CERT_EXPIRY_SECONDS;
        }
        return configuredValidityPeriod;
    }

    /**
     * Check if certificate rotations are disabled.
     *
     * @return true if certificate rotations are disabled
     */
    public boolean isCertificateRotationDisabled() {
        return Coerce.toBoolean(
                configuration.findOrDefault(DEFAULT_DISABLE_CERTIFICATE_ROTATION, PATH_DISABLE_CERTIFICATE_ROTATION));
    }
}
