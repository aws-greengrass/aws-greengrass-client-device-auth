/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cisclient;

import com.aws.iot.evergreen.util.Utils;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class GetConnectivityInfoResponse {
    @JsonProperty("ConnectivityInfo")
    private List<ConnectivityInfoItem> connectivityInfoItems;

    /**
     * Checks if connectivity info has entries.
     * @return true if there is no entry
     */
    public boolean hasEmptyFields() {
        if (connectivityInfoItems == null || connectivityInfoItems.isEmpty()) {
            return true;
        }

        for (ConnectivityInfoItem item : connectivityInfoItems) {
            if (Utils.isEmpty(item.getHostAddress()) || Utils.isEmpty(item.getId()) || item.portNumber == 0) {
                return true;
            }
        }

        return false;
    }
}