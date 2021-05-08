/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.attribute.AttributeProvider;
import com.aws.greengrass.device.attribute.DeviceAttribute;

import java.util.function.Function;

public interface Session {

    /**
     * Get attribute provider by namespace.
     *
     * @param attributeProviderNameSpace Attribute namespace
     * @return Attribute provider
     */
    AttributeProvider get(String attributeProviderNameSpace);

    /**
     * Put attribute provider to the namespace.
     *
     * @param attributeProviderNameSpace  Attribute namespace
     * @param attributeProvider Attribute provider
     * @return Attribute provider put to the session
     */
    AttributeProvider put(String attributeProviderNameSpace, AttributeProvider attributeProvider);

    /**
     * Compute and put attribute provider if the namespace is not occupied.
     *
     * @param attributeProviderNameSpace Attribute namespace
     * @param mappingFunction Mapping function to compute attribute provider
     * @return Attribute provider put to the session
     */
    AttributeProvider computeIfAbsent(String attributeProviderNameSpace,
                                      Function<? super String, ? extends AttributeProvider> mappingFunction);

    /**
     * Get session attribute.
     *
     * @param attributeNamespace Attribute namespace
     * @param attributeName      Attribute name
     * @return Session attribute
     */
    DeviceAttribute getSessionAttribute(String attributeNamespace, String attributeName);
}
