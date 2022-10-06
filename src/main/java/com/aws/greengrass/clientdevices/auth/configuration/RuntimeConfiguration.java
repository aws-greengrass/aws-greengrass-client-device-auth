/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;

import java.util.List;
import java.util.Map;

/**
 * Manages the runtime configuration for the plugin. It allows to read and write
 * to topics under the runtime key. Acts as an adapter from the GG Runtime Topics to the domain.
 * <p>
 * |---- runtime
 * |    |---- ca_passphrase: "..."
 * |    |---- certificates:
 * |         |---- authorities: [...]
 * |    |---- clientDeviceThings:
 * |          |---- schemaVersion:
 * |                |---- thingName:
 * |                      |---- key: value
 * </p>
 */
public final class RuntimeConfiguration {
    public static final String CA_PASSPHRASE_KEY = "ca_passphrase";
    private static final String AUTHORITIES_KEY = "authorities";
    private static final String CERTIFICATES_KEY = "certificates";
    private static final String CLIENT_DEVICE_THINGS_KEY = "clientDeviceThings";
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
     * Retrieves opaque client device thing configuration.
     *
     * @param thingName     Thing name
     * @param schemaVersion Thing config schema version
     * @return config topic mapped to given thing name
     */
    public Topics getClientDeviceThing(String thingName, String schemaVersion) {
        return config.findTopics(CLIENT_DEVICE_THINGS_KEY, schemaVersion, thingName);
    }

    /**
     * Create or replace client device thing details in the config.
     *
     * @param thingName     client device thing name
     * @param thingDetails  thing details
     * @param schemaVersion Thing config schema version
     */
    public void createOrReplaceClientDeviceThing(String thingName,
                                                 Map<String, Object> thingDetails,
                                                 String schemaVersion) {
        Topics clientDeviceThing = config.lookupTopics(CLIENT_DEVICE_THINGS_KEY, schemaVersion, thingName);
        clientDeviceThing.replaceAndWait(thingDetails);
    }

    /**
     * Update client device thing details in the config.
     *
     * @param thingName     client device Thing name
     * @param thingDetails  map of client device Thing details to be updated
     * @param schemaVersion Thing config schema version
     */
    public void updateClientDeviceThing(String thingName, Map<String, Object> thingDetails, String schemaVersion) {
        Topics clientDeviceThing = config.findTopics(CLIENT_DEVICE_THINGS_KEY, schemaVersion, thingName);
        if (clientDeviceThing == null) {
            return;
        }
        thingDetails.forEach((key, value) -> updateClientDeviceThingDetail(clientDeviceThing, key, value));
    }

    @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
    private void updateClientDeviceThingDetail(Topics clientDeviceThing, String detailKey, Object newValue) {
        Topic thingDetail = clientDeviceThing.lookup(detailKey);
        IllegalArgumentException ex = new IllegalArgumentException(
                String.format("Unsupported thing detail value: %s", newValue));
        if (newValue instanceof Integer) {
            thingDetail.withValue((Integer) newValue);
        } else if (newValue instanceof String) {
            thingDetail.withValue((String) newValue);
        } else if (newValue instanceof List<?>) {
            for (Object obj : (List<?>) newValue) {
                if (obj instanceof String) {
                    thingDetail.withValue((List<String>) newValue);
                } else {
                    throw ex;
                }
                break;
            }
        } else {
            throw ex;
        }
    }
}
