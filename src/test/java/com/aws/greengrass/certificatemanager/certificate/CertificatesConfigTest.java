/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CertificatesConfigTest {
    private Topics configurationTopics;
    private CertificatesConfig certificatesConfig;

    @BeforeEach
    public void beforeEach() {
        configurationTopics = Topics.of(new Context(), KernelConfigResolver.CONFIGURATION_CONFIG_KEY, null);
        certificatesConfig = new CertificatesConfig(configurationTopics);
    }

    @Test
    public void GIVEN_defaultConfiguration_WHEN_getServerCertValiditySeconds_THEN_returnsDefaultExpiry() {
        assertThat(certificatesConfig.getServerCertValiditySeconds(),
                is(equalTo(CertificatesConfig.DEFAULT_SERVER_CERT_EXPIRY_SECONDS)));
    }

    @Test
    public void GIVEN_100DayServerValidity_WHEN_getServerCertValiditySeconds_THEN_returnsMaxExpiry() {
        configurationTopics.lookup(CertificatesConfig.PATH_SERVER_CERT_EXPIRY_SECONDS)
                .withValue(2 * CertificatesConfig.MAX_SERVER_CERT_EXPIRY_SECONDS);
        assertThat(certificatesConfig.getServerCertValiditySeconds(),
                is(equalTo(CertificatesConfig.MAX_SERVER_CERT_EXPIRY_SECONDS)));
    }

    @Test
    public void GIVEN_defaultConfiguration_WHEN_getClientCertValiditySeconds_THEN_returnsDefaultExpiry() {
        assertThat(certificatesConfig.getClientCertValiditySeconds(),
                is(equalTo(CertificatesConfig.DEFAULT_CLIENT_CERT_EXPIRY_SECONDS)));
    }

}
