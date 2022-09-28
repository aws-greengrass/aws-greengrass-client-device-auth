/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.StringLiteralAttribute;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Getter
public class Certificate implements AttributeProvider {
    public static final String NAMESPACE = "Certificate";

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    String iotCertificateId;
    Status status;
    Instant lastUpdated;


    // TODO: Needed?
    public Certificate(String iotCertificateId) {
        this(iotCertificateId, Status.INACTIVE);
    }

    /**
     * Construct certificate using the current time as last updated.
     * @param iotCertificateId Certificate ID
     * @param status           Certificate status
     */
    public Certificate(String iotCertificateId, Status status) {
        this(iotCertificateId, status, Instant.MIN);
    }

    /**
     * Construct Certificate.
     * @param iotCertificateId Certificate ID
     * @param status           Certificate status
     * @param lastUpdated      Certificate last updated timestamp
     */
    public Certificate(String iotCertificateId, Status status, Instant lastUpdated) {
        this.iotCertificateId = iotCertificateId;
        this.status = status;
        this.lastUpdated = lastUpdated;
    }

    /**
     * Update certificate status as of the current time.
     * @param status Certificate status
     */
    public void updateStatus(Status status) {
        updateStatus(status, Instant.now());
    }

    /**
     * Update certificate status as of the provided time.
     * @param status      Certificate status
     * @param lastUpdated Timestamp
     */
    public void updateStatus(Status status, Instant lastUpdated) {
        this.status = status;
        this.lastUpdated = lastUpdated;
    }

    /**
     * Check certificate status.
     * @return true if this certificate is active in IoT Core.
     */
    public boolean isActive() {
        return status == Status.ACTIVE;
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
