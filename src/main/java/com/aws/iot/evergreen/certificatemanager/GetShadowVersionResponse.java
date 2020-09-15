/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.certificatemanager;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class GetShadowVersionResponse {
    private State state;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class State {
        private Delta delta;

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class Delta {
            private String version;
        }
    }
}
