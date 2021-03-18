/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.iot.Certificate;

import java.util.HashMap;
import java.util.Map;

public class Session {
    private final Map<String, AttributeProvider> attributeProviderMap;

    // TODO: Replace this with Principal abstraction
    // so that a session can be instantiated using something else
    // e.g. username/password
    public Session(Certificate certificate) {
        attributeProviderMap = new HashMap<>();
        attributeProviderMap.put(certificate.getNamespace(), certificate);
    }

    public void addAttributeProvider(AttributeProvider provider) {
        attributeProviderMap.put(provider.getNamespace(), provider);
    }

    /**
     * Get session provider.
     *
     * @param attributeNamespace Attribute namespace
     *
     * @return Attribute provider associated with this session
     */
    public AttributeProvider getSessionProvider(String attributeNamespace) {
        // TODO: Avoid NPE
        return attributeProviderMap.get(attributeNamespace);
    }

    /**
     * Get session attribute.
     *
     * @param attributeNamespace Attribute namespace
     * @param attributeName      Attribute name
     *
     * @return Session attribute
     */
    public DeviceAttribute getSessionAttribute(String attributeNamespace, String attributeName) {
        // TODO: Avoid NPE
        return attributeProviderMap.get(attributeNamespace).getDeviceAttributes().get(attributeName);
    }
}
