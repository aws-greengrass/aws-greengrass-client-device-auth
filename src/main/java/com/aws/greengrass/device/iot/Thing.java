/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.iot;

import com.aws.greengrass.device.attribute.AttributeProvider;
import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.attribute.StringLiteralAttribute;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Thing implements AttributeProvider {
    public static final String NAMESPACE = "Thing";
    private static final String thingNamePattern = "[a-zA-Z0-9\\-_:]+";

    private final String thingName;

    /**
     * Constructor.
     * @param thingName AWS IoT ThingName
     * @throws IllegalArgumentException If the given ThingName contains illegal characters
     */
    public Thing(String thingName) {
        if (!Pattern.matches(thingNamePattern, thingName)) {
            throw new IllegalArgumentException("Invalid ThingName");
        }
        this.thingName = thingName;
    }

    /**
     * Determine whether this Thing is attached to the given Iot certificate.
     *
     * @param certificate An Iot certificate
     * @param iotControlPlaneBetaClient Beta control plane client
     * @return True if attached, else false
     */
    public boolean isCertificateAttached(Certificate certificate, IotControlPlaneBetaClient iotControlPlaneBetaClient) {
        // TODO: Remove beta workaround - this should call new dataplane API instead of control plane
        List<String> attachedIds = iotControlPlaneBetaClient.listThingCertificatePrincipals(thingName);

        for (String certificateId : attachedIds) {
            String iotCertificate = iotControlPlaneBetaClient.downloadSingleDeviceCertificate(certificateId);
            if (iotCertificate.equals(certificate.getCertificatePem())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public Map<String, DeviceAttribute> getDeviceAttributes() {
        return Collections.singletonMap("ThingName", new StringLiteralAttribute(thingName));
    }
}
