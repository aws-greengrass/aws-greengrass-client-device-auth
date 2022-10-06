/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.iot.dto.CertificateV1;
import com.aws.greengrass.clientdevices.auth.iot.dto.ThingV1;
import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the runtime configuration for the plugin. It allows to read and write
 * to topics under the runtime key. Acts as an adapter from the GG Runtime Topics to the domain.
 * <p>
 * |---- runtime
 * |    |---- ca_passphrase: "..."
 * |    |---- certificates:
 * |         |---- authorities: [...]
 * |
 * |    |---- "clientDeviceThings":
 * |          |---- "v1":
 * |                |---- thingName:
 * |                      |---- "c":
 * |                           |---- certId:lastVerified
 * |    |---- "clientDeviceCerts":
 * |          |---- "v1":
 * |                |---- certificateId:
 * |                      |---- "s": status
 * |                      |---- "l": lastUpdated
 * </p>
 */
public final class RuntimeConfiguration {
    public static final String CA_PASSPHRASE_KEY = "ca_passphrase";
    private static final String AUTHORITIES_KEY = "authorities";
    private static final String CERTIFICATES_KEY = "certificates";
    private static final String THINGS_KEY = "clientDeviceThings";
    private static final String THINGS_V1_KEY = "v1";
    private static final String THINGS_CERTIFICATES_KEY = "c";
    private static final String CERTS_KEY = "clientDeviceCerts";
    private static final String CERTS_V1_KEY = "v1";
    private static final String CERTS_STATUS_KEY = "s";
    private static final String CERTS_STATUS_UPDATED_KEY = "l";

    private final Topics config;


    private RuntimeConfiguration(Topics config) {
        this.config = config;
    }

    public static RuntimeConfiguration from(Topics runtimeTopics) {
       return new RuntimeConfiguration(runtimeTopics);
    }

    /**
     * Returns the runtime configuration value for the ca_passphrase.
     */
    public String getCaPassphrase() {
        Topic caPassphrase = config.lookup(CA_PASSPHRASE_KEY).dflt("");
        return Coerce.toString(caPassphrase);
    }

    /**
     * Updates the configuration value for certificates.
     *
     * @param caCerts list of caCerts
     */
    public void updateCACertificates(List<String> caCerts) {
        Topic caCertsTopic = config.lookup(CERTIFICATES_KEY, AUTHORITIES_KEY);
        caCertsTopic.withValue(caCerts);
    }

    /**
     * Updates the runtime configuration value for ca_passphrase.
     *
     * @param passphrase new passphrase
     */
    public void updateCAPassphrase(String passphrase) {
        Topic caPassphrase = config.lookup(CA_PASSPHRASE_KEY);
        // TODO: This passphrase needs to be encrypted prior to storing in TLOG
        caPassphrase.withValue(passphrase);
    }

    /**
     * Retrieve a Thing.
     *
     * @param thingName ThingName
     * @return Optional of ThingV1 DTO, else empty optional
     */
    public Optional<ThingV1> getThingV1(String thingName) {
        Topics v1ThingTopics = config.findTopics(THINGS_KEY, THINGS_V1_KEY, thingName);

        if (v1ThingTopics == null) {
            return Optional.empty();
        }

        Topics certTopics = v1ThingTopics.lookupTopics(THINGS_CERTIFICATES_KEY);
        Map<String, Long> certMap = new HashMap<>();
        certTopics.forEach(node -> {
            certMap.put(node.getName(), Coerce.toLong(node));
        });

        return Optional.of(new ThingV1(thingName, certMap));
    }

    /**
     * Store a Thing in the Runtime Configuration.
     *
     * @param thing Thing DTO
     */
    public void putThing(ThingV1 thing) {
        if (thing == null) {
            return;
        }

        Topics v1ThingTopics = config.lookupTopics(THINGS_KEY, THINGS_V1_KEY, thing.getThingName());
        Map<String, Object> certMap = new HashMap<>(thing.getCertificates());
        v1ThingTopics.lookupTopics(THINGS_CERTIFICATES_KEY).replaceAndWait(certMap);
    }

    /**
     * Get a certificate.
     *
     * @param certificateId Certificate ID
     * @return  Optional of CertificateV1 DTO, else empty optional
     */
    public Optional<CertificateV1> getCertificateV1(String certificateId) {
        Topics v1CertTopics = config.findTopics(CERTS_KEY, CERTS_V1_KEY, certificateId);

        if (v1CertTopics == null) {
            return Optional.empty();
        }

        Topic statusTopic = v1CertTopics.find(CERTS_STATUS_KEY);
        Topic statusUpdatedTopic = v1CertTopics.find(CERTS_STATUS_UPDATED_KEY);

        CertificateV1.Status status = CertificateV1.Status.UNKNOWN;
        if (statusTopic != null) {
            int topicVal = Coerce.toInt(statusTopic);
            if (topicVal < CertificateV1.Status.values().length) {
                status = CertificateV1.Status.values()[topicVal];
            }
        }

        Long statusUpdated = 0L;
        if (statusUpdated != null) {
            statusUpdated = Coerce.toLong(statusUpdatedTopic);
        }

        return Optional.of(new CertificateV1(certificateId, status, statusUpdated));
    }

    /**
     * Store a certificate in Runtime Configuration.
     *
     * @param cert Certificate DTO
     */
    public void putCertificate(CertificateV1 cert) {
        if (cert == null) {
            return;
        }

        Topics v1CertTopics = config.lookupTopics(CERTS_KEY, CERTS_V1_KEY, cert.getCertificateId());
        v1CertTopics.lookup(CERTS_STATUS_KEY).withValue(cert.getStatus().ordinal());
        v1CertTopics.lookup(CERTS_STATUS_UPDATED_KEY).withValue(cert.getStatusUpdated());
    }
}