/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class SessionImpl extends ConcurrentHashMap<String, AttributeProvider> implements Session {

    static final long serialVersionUID = -1L;

    // TODO: Replace this with Principal abstraction
    // so that a session can be instantiated using something else
    // e.g. username/password
    public SessionImpl(Certificate certificate) {
        super();
        this.put(certificate.getNamespace(), certificate);
    }

    @Override
    public AttributeProvider getAttributeProvider(String attributeProviderNameSpace) {
        return this.get(attributeProviderNameSpace);
    }

    @Override
    public AttributeProvider putAttributeProvider(String attributeProviderNameSpace,
                                                  AttributeProvider attributeProvider) {
        return this.put(attributeProviderNameSpace, attributeProvider);
    }

    @Override
    public AttributeProvider computeAttributeProviderIfAbsent(String attributeProviderNameSpace,
                                                              Function<? super String, ? extends AttributeProvider>
                                                                      mappingFunction) {
        return computeIfAbsent(attributeProviderNameSpace, mappingFunction);
    }

    /**
     * Get session attribute.
     *
     * @param attributeNamespace Attribute namespace
     * @param attributeName      Attribute name
     * @return Session attribute
     */
    @Override
    public DeviceAttribute getSessionAttribute(String attributeNamespace, String attributeName) {
        if (this.getAttributeProvider(attributeNamespace) != null) {
            return this.getAttributeProvider(attributeNamespace).getDeviceAttributes().get(attributeName);
        }
        return null;
    }
}
