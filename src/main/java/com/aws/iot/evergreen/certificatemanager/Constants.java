/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.certificatemanager;

import software.amazon.awssdk.utils.ImmutableMap;

public final class Constants {
    public static final String CIS_SERVICE_NAME = "ConnectivityInformationService";
    private static final String CIS_SHADOW_SUFFIX = "-gci";

    public static final String CSR_COUNTRY = "US";
    public static final String CSR_PROVINCE = "Washington";
    public static final String CSR_LOCALITY = "Seattle";
    public static final String CSR_ORGANIZATION = "Amazon.com Inc.";
    public static final String CSR_ORGANIZATIONAL_UNIT = "Amazon Web Services";
    public static final String KEY_TYPE_RSA = "RSA";
    public static final String RSA_SIGNING_ALGORITHM = "SHA256withRSA";

    public static final ImmutableMap<String, String> SERVICE_SUFFIXES =
            ImmutableMap.of(CIS_SERVICE_NAME, CIS_SHADOW_SUFFIX);

    public static final ImmutableMap<String, String> CSR_CERTIFICATE_SIGNING_ALGORITHM =
            ImmutableMap.of(KEY_TYPE_RSA, RSA_SIGNING_ALGORITHM);

    private Constants() {
    }
}
