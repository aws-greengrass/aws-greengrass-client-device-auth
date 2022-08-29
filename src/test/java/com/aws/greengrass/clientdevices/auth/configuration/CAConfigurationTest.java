/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CA_PASSPHRASE;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CA_TYPE_TOPIC;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_CERTIFICATE_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_PRIVATE_KEY_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CERTIFICATE_AUTHORITY_TOPIC;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.lifecyclemanager.GreengrassService.RUNTIME_STORE_NAMESPACE_TOPIC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CAConfigurationTest {
    private Topics configurationTopics;
    private CAConfiguration caConfiguration;

    @BeforeEach
    void beforeEach() {
        configurationTopics = Topics.of(new Context(), CONFIGURATION_CONFIG_KEY, null);
        caConfiguration = new CAConfiguration(configurationTopics);
    }

    @AfterEach
    void afterEach() throws IOException {
        configurationTopics.getContext().close();
    }

    @Test
    public void GIVEN_cdaDefaultConfiguration_WHEN_getCATypeList_THEN_returnsEmptyList() {
        assertThat(caConfiguration.getCaTypeList(), is(Collections.emptyList()));
    }

    @Test
    public void GIVEN_cdaDefaultConfiguration_WHEN_getCAKeyUri_THEN_returnsNull() {
        assertThat(caConfiguration.getCaPrivateKeyUri(), is(nullValue()));
    }

    @Test
    public void GIVEN_cdaDefaultConfiguration_WHEN_getCACertUri_THEN_returnsNull() {
        assertThat(caConfiguration.getCaCertificateUri(), is(nullValue()));
    }

    @Test
    public void GIVEN_cdaConfiguration_WHEN_getCACertUri_THEN_returnsCACertUri() {
        configurationTopics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_CERTIFICATE_URI)
                .withValue("file:///cert-uri");
        assertThat(caConfiguration.getCaCertificateUri(), is(nullValue()));
        caConfiguration.updateCAConfiguration();
        assertThat(caConfiguration.getCaCertificateUri(), is("file:///cert-uri"));
    }

    @Test
    public void GIVEN_cdaConfiguration_WHEN_getCAKeyUri_THEN_returnsCACertUri() {
        configurationTopics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_PRIVATE_KEY_URI)
                .withValue("file:///key-uri");
        assertThat(caConfiguration.getCaPrivateKeyUri(), is(nullValue()));
        caConfiguration.updateCAConfiguration();
        assertThat(caConfiguration.getCaPrivateKeyUri(), is("file:///key-uri"));
    }

    @Test
    public void GIVEN_cdaConfiguration_WHEN_getCATypeList_THEN_returnsCATypeList() {
        configurationTopics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_TYPE_TOPIC)
                .withValue(Arrays.asList("RSA_2048","EC_DSA"));
        assertThat(caConfiguration.getCaTypeList(), is(Collections.emptyList()));
        caConfiguration.updateCAConfiguration();
        assertThat(caConfiguration.getCaTypeList(), is(Arrays.asList("RSA_2048","EC_DSA")));
    }

    @Test
    public void GIVEN_cdaConfiguration_WHEN_getCaPassphrase_THEN_returnsCAPassphrase() {
        configurationTopics.lookup(RUNTIME_STORE_NAMESPACE_TOPIC, CA_PASSPHRASE)
                .withValue("passphrase");
        assertThat(caConfiguration.getCaPassphrase(), is(nullValue()));
        caConfiguration.updateCAConfiguration();
        assertThat(caConfiguration.getCaPassphrase(), is("passphrase"));
    }

}
