/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ShadowDeltaMessage {
    private State state;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class State {
        private String version;
    }
}
