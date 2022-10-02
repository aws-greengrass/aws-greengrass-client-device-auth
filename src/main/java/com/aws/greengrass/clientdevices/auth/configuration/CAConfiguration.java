/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.Utils;
import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Represents the certificateAuthority and ca_type part of the component configuration. Acts as an adapter
 * from the GG Topics to the domain.
 * <p>
 * |---- configuration
 * |    |---- certificateAuthority:
 * |          |---- privateKeyUri: "..."
 * |          |---- certificateUri: "..."
 * |          |---- caType: [...]
 * </p>
 */
@Getter
public final class CAConfiguration {
    public static final String CA_CERTIFICATE_URI = "certificateUri";
    public static final String CA_PRIVATE_KEY_URI = "privateKeyUri";
    public static final String DEPRECATED_CA_TYPE_KEY = "ca_type";
    public static final String CA_TYPE_KEY = "caType";
    public static final String CERTIFICATE_AUTHORITY_TOPIC = "certificateAuthority";
    private static final Logger logger = LogManager.getLogger(CAConfiguration.class);

    private CertificateStore.CAType caType;
    private List<String> caTypeList;
    private Optional<URI> privateKeyUri;
    private Optional<URI> certificateUri;


    private CAConfiguration(List<String> caTypes, CertificateStore.CAType caType,
                            Optional<URI> privateKeyUri, Optional<URI> certificateUri) {
        this.caType = caType;
        this.caTypeList = caTypes;
        this.privateKeyUri = privateKeyUri;
        this.certificateUri = certificateUri;
    }

    /**
     * Factory method for creating an immutable CAConfiguration from the service configuration.
     *
     * @param configurationTopics the configuration key of the service configuration
     */
    public static Result<CAConfiguration> from(Topics configurationTopics) {
        Topics certAuthorityTopic = configurationTopics.lookupTopics(CERTIFICATE_AUTHORITY_TOPIC);
        Result<Optional<URI>> privateKeyUriResult = getCaPrivateKeyUriFromConfiguration(certAuthorityTopic);
        Result<Optional<URI>> certificateUriResult = getCaCertificateUriFromConfiguration(certAuthorityTopic);

        CAConfiguration caConfiguration = new CAConfiguration(
                getCaTypeListFromConfiguration(configurationTopics),
                getCaTypeFromConfiguration(configurationTopics),
                privateKeyUriResult.get(),
                certificateUriResult.get()
        );

        if (privateKeyUriResult.isError()) {
            return Result.error(caConfiguration, privateKeyUriResult.getError());
        }

        if (certificateUriResult.isError()) {
            return Result.error(caConfiguration, certificateUriResult.getError());
        }

        Optional<URI> privateKeyUri = privateKeyUriResult.get();
        Optional<URI> certificateUri = certificateUriResult.get();

        if (privateKeyUri.isPresent() != certificateUri.isPresent()) {
            return Result.error(caConfiguration, new InvalidConfigurationException(
                    CA_PRIVATE_KEY_URI + " and " + CA_CERTIFICATE_URI + " must have a value."));
        }

        return Result.ok(caConfiguration);
    }

    /**
     * Checks if the bringing your own certificate configuration was provided. For it to be valid both the
     * privateKeyUri and the certificateUri must be provided.
     */
    public boolean isUsingCustomCA() {
        return privateKeyUri.isPresent() && certificateUri.isPresent();
    }

    /**
     * Verifies if the configuration for the certificateAuthority has changed, given a previous
     * configuration.
     *
     * @param config  CAConfiguration
     */
    public boolean isEqual(CAConfiguration config) {
        if (config == null) {
            return false;
        }

        return Objects.equals(config.getCertificateUri(), certificateUri)
                && Objects.equals(config.getPrivateKeyUri(), privateKeyUri)
                && Objects.equals(config.getCaType(), caType);
    }

    private static List<String> getCaTypeListFromConfiguration(Topics configurationTopic) {
        // NOTE: This should be a list of CertificateStore.CAType and not any random Strings
        Topic caTypeTopic = configurationTopic.lookup(CERTIFICATE_AUTHORITY_TOPIC, CA_TYPE_KEY);
        if (caTypeTopic.getOnce() != null) {
            return Coerce.toStringList(caTypeTopic.getOnce());
        }

        // NOTE: Ensure backwards compat with v.2.2.2 we are moving the ca_type key to be under
        // certificateAuthority and changing its name to caType. (ONLY REMOVE AFTER IT IS SAFE)
        Topic deprecatedCaTypeTopic = configurationTopic.lookup(DEPRECATED_CA_TYPE_KEY);
        if (deprecatedCaTypeTopic.getOnce() != null) {
            return Coerce.toStringList(deprecatedCaTypeTopic.getOnce());
        }

        return Collections.emptyList();
    }

    private static CertificateStore.CAType getCaTypeFromConfiguration(Topics configurationTopic) {
        List<String> caTypeList = getCaTypeListFromConfiguration(configurationTopic);
        logger.atDebug().kv("CA type", caTypeList).log("CA type list updated");

        if (caTypeList.isEmpty()) {
            logger.atDebug().log("CA type list null or empty. Defaulting to RSA");
            return CertificateStore.CAType.RSA_2048;
        }

        if (caTypeList.size() > 1) {
            logger.atWarn().log("Only one CA type is supported. Ignoring subsequent CAs in the list.");
        }

        String caType = caTypeList.get(0);
        return CertificateStore.CAType.valueOf(caType);
    }

    private static Result<URI> getUri(String rawUri) {
        URI uri;
        try {
            uri = new URI(rawUri);
        } catch (URISyntaxException e) {
           return Result.error(e);
        }

        if (uri.getScheme() == null) {
            return Result.error(new URISyntaxException(rawUri, "Uri is missing the scheme"));
        }

        Stream<String> supportedSchemes = Stream.of("file", "pkcs11");
        if (supportedSchemes.noneMatch(uri.getScheme()::contains)) {
           return Result.error(
                   new URISyntaxException(rawUri, "Unsupported URI scheme. Supported: " + supportedSchemes));
        }

        return Result.ok(uri);
    }

    private static Result<Optional<URI>> getCaPrivateKeyUriFromConfiguration(Topics certAuthorityTopic) {
        String privateKeyUri = Coerce.toString(certAuthorityTopic.findOrDefault("", CA_PRIVATE_KEY_URI));

        if (Utils.isEmpty(privateKeyUri)) {
            return Result.ok(Optional.empty());
        }

        return getUri(privateKeyUri).map(Optional::ofNullable);
    }

    private static Result<Optional<URI>> getCaCertificateUriFromConfiguration(Topics certAuthorityTopic) {
        String certificateUri = Coerce.toString(certAuthorityTopic.findOrDefault("", CA_CERTIFICATE_URI));

        if (Utils.isEmpty(certificateUri)) {
            return Result.ok(Optional.empty());
        }

        return getUri(certificateUri).map(Optional::ofNullable);
    }
}
