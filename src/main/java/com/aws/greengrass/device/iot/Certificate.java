/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.iot;

import com.aws.greengrass.device.attribute.AttributeProvider;
import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.attribute.StringLiteralAttribute;
import lombok.NonNull;
import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value
public class Certificate implements AttributeProvider {
    public static final String NAMESPACE = "Certificate";

    @NonNull
    String iotCertificateId; // Needed for certificate revocation

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public Map<String, DeviceAttribute> getDeviceAttributes() {
        return Collections.singletonMap("CertificateId", new StringLiteralAttribute(getIotCertificateId()));
    }
}
