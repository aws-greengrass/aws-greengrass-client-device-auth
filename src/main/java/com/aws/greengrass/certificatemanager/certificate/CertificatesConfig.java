/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;

public class CertificatesConfig {
    static final int MAX_SERVER_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 10; // 10 days
    static final int MIN_SERVER_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 2; // 2 days
    static final int DEFAULT_SERVER_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 7; // 7 days
    static final int DEFAULT_CLIENT_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 7; // 7 days

    private static final String CERTIFICATES_CONFIGURATION = "certificates";
    private static final String SERVER_CERT_VALIDITY_SECONDS = "serverCertificateValiditySeconds";

    static final String[] PATH_SERVER_CERT_EXPIRY_SECONDS =
            {KernelConfigResolver.CONFIGURATION_CONFIG_KEY, CERTIFICATES_CONFIGURATION, SERVER_CERT_VALIDITY_SECONDS};

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
        int configuredValidityPeriod = Coerce.toInt(configuration.findOrDefault(DEFAULT_SERVER_CERT_EXPIRY_SECONDS,
                PATH_SERVER_CERT_EXPIRY_SECONDS));
        if (configuredValidityPeriod > MAX_SERVER_CERT_EXPIRY_SECONDS) {
            return MAX_SERVER_CERT_EXPIRY_SECONDS;
        } else if (configuredValidityPeriod < MIN_SERVER_CERT_EXPIRY_SECONDS) {
            return MIN_SERVER_CERT_EXPIRY_SECONDS;
        }
        return configuredValidityPeriod;
    }

    /**
     * Get server certificate validity period.
     *
     * @return Client certificate validity in seconds
     */
    public int getClientCertValiditySeconds() {
        return DEFAULT_CLIENT_CERT_EXPIRY_SECONDS;
    }
}
