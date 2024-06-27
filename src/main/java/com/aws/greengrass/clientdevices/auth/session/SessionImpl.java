/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.session.attribute.Attribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;

import java.util.concurrent.ConcurrentHashMap;

public class SessionImpl extends ConcurrentHashMap<String, AttributeProvider> implements Session {

    static final long serialVersionUID = -1L;

    /**
     * Create a Session from a list of attribute providers.
     *
     * @param providers list of attribute providers
     */
    public SessionImpl(AttributeProvider... providers) {
        super();
        for (AttributeProvider provider : providers) {
            this.put(provider.getNamespace(), provider);
        }
    }

    @Override
    public AttributeProvider getAttributeProvider(String namespace) {
        return this.get(namespace);
    }

    /**
     * Get session attribute.
     *
     * @param attribute     attribute
     * @return Session attribute
     */
    @Override
    public DeviceAttribute getSessionAttribute(Attribute attribute) {
        if (this.getAttributeProvider(attribute.getNamespace()) != null) {
            return this.getAttributeProvider(attribute.getNamespace()).getDeviceAttribute(attribute.getName());
        }
        return null;
    }
}
