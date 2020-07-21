/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.dcm;

import software.amazon.awssdk.utils.ImmutableMap;

public final class Constants {
    public static final String CIS_SERVICE_NAME = "ConnectivityInformationService";
    public static final String GCM_SERVICE_NAME = "CertificateManagerService";
    private static final String CIS_SHADOW_SUFFIX = "-gci";
    private static final String GCM_SHADOW_SUFFIX = "-gcm";

    public static final ImmutableMap<String, String> SERVICE_SUFFIXES =
            ImmutableMap.of(CIS_SERVICE_NAME, CIS_SHADOW_SUFFIX, GCM_SERVICE_NAME, GCM_SHADOW_SUFFIX);

    private Constants() {
    }
}
