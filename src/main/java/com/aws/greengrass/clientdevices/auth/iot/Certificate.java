/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.StringLiteralAttribute;
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
