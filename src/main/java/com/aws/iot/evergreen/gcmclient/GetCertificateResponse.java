/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.gcmclient;

import com.aws.iot.evergreen.util.Utils;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetCertificateResponse {
    private String certificate;

    public boolean hasEmptyFields() {
        return Utils.isEmpty(certificate);
    }
}
