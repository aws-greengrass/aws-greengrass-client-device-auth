/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.aws.greengrass.device.iot;

public interface IotAuthClient {
    String getActiveCertificateId(String certificatePem);

    boolean isThingAttachedToCertificate(Thing thing, Certificate certificate);
}
