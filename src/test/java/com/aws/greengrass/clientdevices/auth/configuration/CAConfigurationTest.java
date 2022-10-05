/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;

import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_CERTIFICATE_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_PRIVATE_KEY_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_TYPE_KEY;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CERTIFICATE_AUTHORITY_TOPIC;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.DEPRECATED_CA_TYPE_KEY;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CAConfigurationTest {
    private Topics configurationTopics;

    @BeforeEach
    void beforeEach() {
        configurationTopics = Topics.of(new Context(), CONFIGURATION_CONFIG_KEY, null);
    }

    @AfterEach
    void afterEach() throws IOException {
        configurationTopics.getContext().close();
    }

    @Test
    public void GIVEN_cdaDefaultConfiguration_WHEN_getCATypeList_THEN_returnsEmptyList() throws URISyntaxException,
            InvalidConfigurationException {
        CAConfiguration caConfiguration = CAConfiguration.from(configurationTopics);
        assertThat(caConfiguration.getCaTypeList(), is(Collections.emptyList()));
    }

    @Test
    public void GIVEN_cdaDefaultConfiguration_WHEN_getCAKeyUri_THEN_returnsNull() throws URISyntaxException,
            InvalidConfigurationException {
        CAConfiguration caConfiguration = CAConfiguration.from(configurationTopics);
        assertFalse(caConfiguration.getPrivateKeyUri().isPresent());
    }

    @Test
    public void GIVEN_cdaDefaultConfiguration_WHEN_getCACertUri_THEN_returnsNull() throws URISyntaxException,
            InvalidConfigurationException {
        CAConfiguration caConfiguration = CAConfiguration.from(configurationTopics);
        assertFalse(caConfiguration.getCertificateUri().isPresent());
    }

    @Test
    public void GIVEN_cdaConfiguration_WHEN_getCACertUri_THEN_returnsCACertUri() throws URISyntaxException,
            InvalidConfigurationException {
        CAConfiguration caConfiguration = CAConfiguration.from(configurationTopics);
        assertFalse(caConfiguration.getCertificateUri().isPresent());
        configurationTopics.lookup(CERTIFICATE_AUTHORITY_TOPIC, CA_PRIVATE_KEY_URI)
                .withValue("file:///key-uri");
        configurationTopics.lookup(CERTIFICATE_AUTHORITY_TOPIC, CA_CERTIFICATE_URI)
                .withValue("file:///cert-uri");
        caConfiguration = CAConfiguration.from(configurationTopics);
        assertThat(caConfiguration.getCertificateUri().get(), is(new URI("file:///cert-uri")));
    }

    @Test
    public void GIVEN_cdaConfiguration_WHEN_getCAKeyUri_THEN_returnsCACertUri() throws URISyntaxException,
            InvalidConfigurationException {
        CAConfiguration caConfiguration = CAConfiguration.from(configurationTopics);
        assertFalse(caConfiguration.getPrivateKeyUri().isPresent());
        configurationTopics.lookup(CERTIFICATE_AUTHORITY_TOPIC, CA_PRIVATE_KEY_URI)
                .withValue("file:///key-uri");
        configurationTopics.lookup(CERTIFICATE_AUTHORITY_TOPIC, CA_CERTIFICATE_URI)
                .withValue("file:///cert-uri");
        caConfiguration = CAConfiguration.from(configurationTopics);
        assertThat(caConfiguration.getPrivateKeyUri().get(), is(new URI("file:///key-uri")));
    }

    @Test
    public void GIVEN_hsmCdaConfiguration_WHEN_getCAKeyUri_THEN_returnsCACertUri() throws URISyntaxException,
            InvalidConfigurationException {
        CAConfiguration caConfiguration = CAConfiguration.from(configurationTopics);
        assertFalse(caConfiguration.getPrivateKeyUri().isPresent());
        configurationTopics.lookup(CERTIFICATE_AUTHORITY_TOPIC, CA_PRIVATE_KEY_URI)
                .withValue("pkcs11:object=test;CustomerRootCA;type=private");
        configurationTopics.lookup(CERTIFICATE_AUTHORITY_TOPIC, CA_CERTIFICATE_URI)
                .withValue("pkcs11:object=test;CustomerRootCA;type=cert");
        caConfiguration = CAConfiguration.from(configurationTopics);
        assertThat(
            caConfiguration.getPrivateKeyUri().get(), is(new URI("pkcs11:object=test;CustomerRootCA;type=private")));
    }

    @Test
    public void GIVEN_pathInsteadOfUri_WHEN_configurationLoaded_THEN_itRaisesAnException() throws URISyntaxException {
        configurationTopics.lookup(CERTIFICATE_AUTHORITY_TOPIC, CA_PRIVATE_KEY_URI)
                .withValue("/root/tls/intermediate/certs/intermediate.cacert.pem");

        assertThrows(URISyntaxException.class, () -> { CAConfiguration.from(configurationTopics); });
    }

    @Test
    public void GIVEN_cdaConfiguration_WHEN_getCATypeList_THEN_returnsCATypeList() throws URISyntaxException,
            InvalidConfigurationException {
        configurationTopics.lookup(CERTIFICATE_AUTHORITY_TOPIC, CA_TYPE_KEY)
                .withValue(Arrays.asList("RSA_2048","ECDSA_P256"));
        CAConfiguration caConfiguration = CAConfiguration.from(configurationTopics);
        assertThat(caConfiguration.getCaType(), is(CertificateStore.CAType.RSA_2048));
        assertThat(caConfiguration.getCaTypeList(), is(Arrays.asList("RSA_2048","ECDSA_P256")));

    }

    @Test
    public void GIVEN_oldCdaConfiguration_WHEN_reading_ca_type_THEN_returns_ca_type() throws URISyntaxException,
            InvalidConfigurationException {
        // NOTE: This test is to ensure we are backwards compatible with the v.2.2.2
        // we are changing how/where ca_type is stored
        configurationTopics.lookup(DEPRECATED_CA_TYPE_KEY)
                .withValue(Arrays.asList("ECDSA_P256"));
        CAConfiguration caConfiguration = CAConfiguration.from(configurationTopics);
        assertThat(caConfiguration.getCaType(), is(CertificateStore.CAType.ECDSA_P256));
        assertThat(caConfiguration.getCaTypeList(), is(Arrays.asList("ECDSA_P256")));

    }

    @Test
    public void GIVEN_oldCdaConfiguration_and_newCdaConfiguration_WHEN_reading_caType_THEN_newValueRead() throws
            URISyntaxException, InvalidConfigurationException {
        // Old path - pre v.2.2.2
        configurationTopics.lookup(DEPRECATED_CA_TYPE_KEY)
                .withValue(Arrays.asList("ECDSA_P256"));
        // New path - post v.2.2.2
        configurationTopics.lookup(CERTIFICATE_AUTHORITY_TOPIC, CA_TYPE_KEY)
                .withValue(Arrays.asList("RSA_2048"));
        CAConfiguration caConfiguration = CAConfiguration.from(configurationTopics);
        assertThat(caConfiguration.getCaType(), is(CertificateStore.CAType.RSA_2048));
        assertThat(caConfiguration.getCaTypeList(), is(Arrays.asList("RSA_2048")));

    }
}