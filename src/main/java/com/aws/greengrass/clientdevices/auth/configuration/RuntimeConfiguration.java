/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;

import java.util.Collections;
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
 * |          |---- thingName:
 * |                |---- key: "value"
 * |
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
     * Update (or create if not available) opaque client device thing details in the config.
     *
     * @param thingName    client device Thing name
     * @param thingDetails opaque client device Thing details
     */
    public void updateClientDeviceThing(String thingName, Map<String, Object> thingDetails) {
        Topics clientDeviceThings = config.lookupTopics(CLIENT_DEVICE_THINGS_KEY);
        Topics clientDeviceThing = clientDeviceThings.lookupTopics(thingName);
        clientDeviceThing.replaceAndWait(thingDetails);
    }

    /**
     * Retrieves opaque client device thing details from the config.
     *
     * @param thingName Thing name
     * @return map of thing details
     */
    public Map<String, Object> getClientDeviceThing(String thingName) {
        Topics clientDeviceThings = config.findTopics(CLIENT_DEVICE_THINGS_KEY);
        if (clientDeviceThings == null || clientDeviceThings.findTopics(thingName) == null
                || clientDeviceThings.findTopics(thingName).isEmpty()) {
            return Collections.emptyMap();
        } else {
            return clientDeviceThings.findTopics(thingName).toPOJO();
        }
    }
}
