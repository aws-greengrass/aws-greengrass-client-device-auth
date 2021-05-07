/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.iot;

import com.aws.greengrass.device.attribute.AttributeProvider;
import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.attribute.StringLiteralAttribute;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;

public class Certificate implements AttributeProvider {
    public static final String NAMESPACE = "Certificate";

    @Getter
    private final String certificatePem;
    private String certificateId; // Needed in case we cannot talk to IoT
    private String iotCertificateId; // Needed for certificate revocation
    private final IotAuthClient iotAuthClient;

    public Certificate(String certificatePem, IotAuthClient iotAuthClient) {
        this.certificatePem = certificatePem;
        this.iotAuthClient = iotAuthClient;
    }

    /**
     * Retrieves internal Greengrass certificate ID.
     *
     * @return Certificate ID
     */
    @SuppressWarnings("PMD.AvoidPrintStackTrace")
    public String getId() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            certificateId = digest.digest(certificatePem.getBytes(StandardCharsets.UTF_8)).toString();
        } catch (NoSuchAlgorithmException e) {
            // TODO: log error
            e.printStackTrace();
        }

        return certificateId;
    }

    /**
     * Retrieves AWS IoT certificate ID.
     *
     * @return AWS IoT Certificate ID
     */
    public String getIotCertificateId() {
        //TODO fix thread safety
        if (iotCertificateId == null) {
            iotCertificateId = iotAuthClient.getActiveCertificateId(certificatePem);
        }

        return iotCertificateId;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public Map<String, DeviceAttribute> getDeviceAttributes() {
        return Collections.singletonMap("CertificateId", new StringLiteralAttribute(getIotCertificateId()));
    }
}
