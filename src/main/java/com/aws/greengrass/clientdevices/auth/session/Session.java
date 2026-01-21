/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.session.attribute.Attribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;

public interface Session {

    /**
     * Get attribute provider by namespace.
     *
     * @param namespace attribute provider namespace
     * @return Attribute provider
     */
    AttributeProvider getAttributeProvider(String namespace);

    /**
     * Get session attribute.
     *
     * @param attribute attribute
     * @return Session attribute
     */
    DeviceAttribute getSessionAttribute(Attribute attribute);
}
