/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.helpers;

import lombok.Setter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class ClockFake extends Clock {
    @Setter
    private Instant currentInstant;

    public ClockFake() {
        super();
        currentInstant = Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneId.systemDefault();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(currentInstant, ZoneId.systemDefault());
    }

    @Override
    public Instant instant() {
        return currentInstant;
    }
}
