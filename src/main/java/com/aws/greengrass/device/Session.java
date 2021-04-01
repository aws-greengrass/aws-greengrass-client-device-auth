/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.attribute.AttributeProvider;
import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.iot.Certificate;

import java.util.concurrent.ConcurrentHashMap;

public class Session extends ConcurrentHashMap<String, AttributeProvider> {

    static final long serialVersionUID = -1L;

    // TODO: Replace this with Principal abstraction
    // so that a session can be instantiated using something else
    // e.g. username/password
    public Session(Certificate certificate) {
        super();
        this.put(certificate.getNamespace(), certificate);
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
        return this.get(attributeNamespace).getDeviceAttributes().get(attributeName);
    }
}
