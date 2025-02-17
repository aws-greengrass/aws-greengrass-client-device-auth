/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;
import com.aws.greengrass.clientdevices.auth.iot.dto.CertificateV1DTO;
import com.aws.greengrass.clientdevices.auth.iot.dto.ThingAssociationV1DTO;
import com.aws.greengrass.clientdevices.auth.iot.dto.ThingDescriptionV1DTO;
import com.aws.greengrass.clientdevices.auth.iot.dto.ThingV1DTO;
import com.aws.greengrass.config.Node;
import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;
import lombok.NonNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages the runtime configuration for the plugin. It allows to read and write to topics under the runtime key. Acts
 * as an adapter from the GG Runtime Topics to the domain.
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
 * |    |---- "clientDeviceThingAssociations":
 * |          |---- "v1":
 * |                |---- associations: [...]
 * |                |---- "l": lastUpdated
 * |    |---- "clientDeviceThingDescription":
 * |          |---- "v1":
 * |                |---- thingName:
 * |                      |---- "attributes":
 * |                           |---- k:v
 * |                      |---- "l": lastUpdated
 * |    |---- "hostAddresses":
 * |---- <source>:
 * |---- [...]
 * </p>
 */
public final class RuntimeConfiguration {
    public static final String CA_PASSPHRASE_KEY = "ca_passphrase";
    private static final String AUTHORITIES_KEY = "authorities";
    private static final String CERTIFICATES_KEY = "certificates";
    static final String THINGS_KEY = "clientDeviceThings";
    static final String THINGS_V1_KEY = "v1";
    static final String THINGS_CERTIFICATES_KEY = "c";
    static final String CERTS_KEY = "clientDeviceCerts";
    static final String CERTS_V1_KEY = "v1";
    static final String CERTS_STATUS_KEY = "s";
    static final String CERTS_STATUS_UPDATED_KEY = "l";
    private static final String HOST_ADDRESSES_KEY = "hostAddresses";

    private static final String ASSOCIATIONS_KEY = "clientDeviceThingAssociations";
    private static final String ASSOCIATIONS_V1 = "v1";
    private static final String DESCRIPTION_KEY = "clientDeviceThingDescription";
    private static final String DESCRIPTION_V1 = "v1";
    private static final String LAST_UPDATED_KEY = "l";
    private static final String ASSOCIATIONS_PROP_KEY = "associations";
    private static final String ATTRIBUTES_PROP_KEY = "attributes";

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
        caPassphrase.withValue(passphrase);
    }

    /**
     * Retrieve a Thing.
     *
     * @param thingName ThingName
     * @return Optional of ThingV1 DTO, else empty optional
     */
    public Optional<ThingV1DTO> getThingV1(String thingName) {
        Topics v1ThingTopics = config.findTopics(THINGS_KEY, THINGS_V1_KEY, thingName);

        if (v1ThingTopics == null) {
            return Optional.empty();
        }

        Topics certTopics = v1ThingTopics.findTopics(THINGS_CERTIFICATES_KEY);
        if (certTopics == null || certTopics.isEmpty()) {
            return Optional.of(new ThingV1DTO(thingName, Collections.emptyMap()));
        }
        Map<String, Long> certMap = new HashMap<>();
        certTopics.forEach(node -> {
            certMap.put(node.getName(), Coerce.toLong(node));
        });

        return Optional.of(new ThingV1DTO(thingName, certMap));
    }

    /**
     * Store a Thing in the Runtime Configuration.
     *
     * @param thing Thing DTO
     */
    public void putThing(@NonNull ThingV1DTO thing) {
        Topics v1ThingTopics = getOrRepairTopics(config, THINGS_KEY, THINGS_V1_KEY, thing.getThingName());
        Map<String, Object> certMap = new HashMap<>(thing.getCertificates());
        getOrRepairTopics(v1ThingTopics, THINGS_CERTIFICATES_KEY).replaceAndWait(certMap);
    }

    /**
     * Removes a v1 Thing from the Runtime Configuration.
     *
     * @param thingName Thing name
     */
    public void removeThingV1(String thingName) {
        Node v1ThingNode = config.findNode(THINGS_KEY, THINGS_V1_KEY, thingName);
        if (v1ThingNode != null) {
            v1ThingNode.remove();
        }
    }

    /**
     * Get a certificate.
     *
     * @param certificateId Certificate ID
     * @return Optional of CertificateV1 DTO, else empty optional
     */
    public Optional<CertificateV1DTO> getCertificateV1(String certificateId) {
        Topics v1CertTopics = config.findTopics(CERTS_KEY, CERTS_V1_KEY, certificateId);

        if (v1CertTopics == null) {
            return Optional.empty();
        }

        Topic statusTopic = v1CertTopics.find(CERTS_STATUS_KEY);
        Topic statusUpdatedTopic = v1CertTopics.find(CERTS_STATUS_UPDATED_KEY);

        CertificateV1DTO.Status status = CertificateV1DTO.Status.UNKNOWN;
        if (statusTopic != null) {
            int topicVal = Coerce.toInt(statusTopic);
            if (topicVal < CertificateV1DTO.Status.values().length) {
                status = CertificateV1DTO.Status.values()[topicVal];
            }
        }

        Long statusUpdated = 0L;
        if (statusUpdatedTopic != null) {
            statusUpdated = Coerce.toLong(statusUpdatedTopic);
        }

        return Optional.of(new CertificateV1DTO(certificateId, status, statusUpdated));
    }

    /**
     * Store a certificate in Runtime Configuration.
     *
     * @param cert Certificate DTO
     */
    public void putCertificate(@NonNull CertificateV1DTO cert) {
        Topics v1CertTopics = getOrRepairTopics(config, CERTS_KEY, CERTS_V1_KEY, cert.getCertificateId());
        v1CertTopics.lookup(CERTS_STATUS_KEY).withValue(cert.getStatus().ordinal());
        v1CertTopics.lookup(CERTS_STATUS_UPDATED_KEY).withValue(cert.getStatusUpdated());
    }

    /**
     * Removes a v1 Certificate from the Runtime Configuration.
     *
     * @param certificateId certificate id
     */
    public void removeCertificateV1(String certificateId) {
        Node v1CertNode = config.findNode(CERTS_KEY, CERTS_V1_KEY, certificateId);
        if (v1CertNode != null) {
            v1CertNode.remove();
        }
    }

    /**
     * Persist thing association dto.
     *
     * @param dto dto
     */
    public void putThingAssociationV1(ThingAssociationV1DTO dto) {
        Topics t = getOrRepairTopics(config, ASSOCIATIONS_KEY, ASSOCIATIONS_V1);
        t.lookup(ASSOCIATIONS_PROP_KEY).withValue(new ArrayList<>(dto.getAssociatedThingNames()));
        t.lookup(LAST_UPDATED_KEY).withValue(dto.getLastUpdated().toEpochSecond(ZoneOffset.UTC));
    }

    /**
     * Get things associated with the core device.
     *
     * @return dto
     */
    public Optional<ThingAssociationV1DTO> getThingAssociationV1() {
        Topics t = config.findTopics(ASSOCIATIONS_KEY, ASSOCIATIONS_V1);
        if (t == null) {
            return Optional.empty();
        }

        Set<String> thingNames = new HashSet<>();
        Topic associationsTopic = t.find(ASSOCIATIONS_PROP_KEY);
        if (associationsTopic != null) {
            thingNames.addAll(Coerce.toStringList(associationsTopic));
        }

        LocalDateTime lastFetched = null;
        Topic lastFetchedTopic = t.find(LAST_UPDATED_KEY);
        if (lastFetchedTopic != null) {
            lastFetched = LocalDateTime.ofInstant(Instant.ofEpochMilli(Coerce.toLong(lastFetchedTopic)),
                    ZoneId.of("UTC"));
        }
        return Optional.of(new ThingAssociationV1DTO(thingNames, lastFetched));
    }

    /**
     * Persist thing description dto.
     *
     * @param dto dto
     */
    public void putThingDescriptionV1(ThingDescriptionV1DTO dto) {
        Topics t = getOrRepairTopics(config, DESCRIPTION_KEY, DESCRIPTION_V1, dto.getThingName());
        t.lookup(LAST_UPDATED_KEY).withValue(dto.getLastUpdated().toEpochSecond(ZoneOffset.UTC));
        Map<String, Object> attrs = new HashMap<>(dto.getAttributes());
        getOrRepairTopics(t, ATTRIBUTES_PROP_KEY).replaceAndWait(attrs);
    }

    /**
     * Get cached IoT describe-thing response. Currently, we only care about the attributes field.
     *
     * @param thingName thing name
     * @return dto
     */
    public Optional<ThingDescriptionV1DTO> getThingDescriptionV1(String thingName) {
        Topics t = config.findTopics(DESCRIPTION_KEY, DESCRIPTION_V1, thingName);
        if (t == null) {
            return Optional.empty();
        }

        Map<String, String> attrs = new HashMap<>();
        Topics attributesTopic = t.findTopics(ATTRIBUTES_PROP_KEY);
        if (attributesTopic != null && !attributesTopic.isEmpty()) {
            attributesTopic.forEach(node -> attrs.put(node.getName(), Coerce.toString(node)));
        }

        LocalDateTime lastFetched = null;
        Topic lastFetchedTopic = t.find(LAST_UPDATED_KEY);
        if (lastFetchedTopic != null) {
            lastFetched = LocalDateTime.ofInstant(Instant.ofEpochMilli(Coerce.toLong(lastFetchedTopic)),
                    ZoneId.of("UTC"));
        }
        return Optional.of(new ThingDescriptionV1DTO(thingName, attrs, lastFetched));
    }

    private Topics getOrRepairTopics(Topics root, String... path) {
        try {
            return root.lookupTopics(path);
        } catch (IllegalArgumentException e) {
            return repairTopics(root, path);
        }
    }

    private Topics repairTopics(Topics root, String... path) {
        Topics currentNode = root;
        for (String topic : path) {
            Node tempNode = currentNode.findNode(topic);
            if (tempNode instanceof Topics) {
                currentNode = (Topics) tempNode;
            } else {
                tempNode.remove();
                break;
            }
        }
        return root.lookupTopics(path);
    }

    /**
     * Returns all the Things that have been stored.
     */
    public Stream<ThingV1DTO> getAllThingsV1() {
        Topics v1ThingTopics = config.findTopics(THINGS_KEY, THINGS_V1_KEY);

        if (v1ThingTopics == null) {
            return Stream.empty();
        }

        return v1ThingTopics.children.keySet().stream().map(Coerce::toString).map(this::getThingV1).map(Optional::get);
    }


    /**
     * Returns all the stored certificates under.
     * |    |---- "clientDeviceCerts":
     * |          |---- "v1":
     * |                |---- certificateId:
     */
    public Stream<CertificateV1DTO> getAllCertificatesV1() {
        Topics v1CertTopics = config.findTopics(CERTS_KEY, CERTS_V1_KEY);

        if (v1CertTopics == null) {
            return Stream.empty();
        }

        return v1CertTopics.children.keySet().stream().map(Coerce::toString).map(this::getCertificateV1)
                .map(Optional::get);
    }

    /**
     * Put hostAddresses config.
     *
     * @param source        connectivity information source
     * @param hostAddresses host addresses
     */
    public void putHostAddressForSource(String source, Set<HostAddress> hostAddresses) {
        config.lookup(HOST_ADDRESSES_KEY, source)
                .withValue(hostAddresses.stream().map(HostAddress::getHost).collect(Collectors.toList()));
    }

    /**
     * Get aggregated hostAddresses config.
     *
     * @return set of host addresses
     */
    public Set<HostAddress> getAggregatedHostAddresses() {
        Topics hostAddressesTopics = config.lookupTopics(HOST_ADDRESSES_KEY);
        if (hostAddressesTopics == null) {
            return Collections.emptySet();
        }

        Set<HostAddress> connectivityInfo = new HashSet<>();
        for (Object addrs : hostAddressesTopics.toPOJO().values()) {
            if (!(addrs instanceof Collection)) {
                continue;
            }
            for (Object addr : (Collection<?>) addrs) {
                if (!(addr instanceof String)) {
                    continue;
                }
                connectivityInfo.add(HostAddress.of((String) addr));
            }
        }
        return connectivityInfo;
    }
}
