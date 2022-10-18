/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.util.Pair;

import java.time.Instant;
import java.time.temporal.TemporalUnit;

public interface Trustable {
    /**
     * Provides the time duration for which the entity is trusted.
     *
     * @return duration with the time unit
     */
    Pair<Integer, TemporalUnit> getTrustDuration();

    /**
     * Checks whether the trust has expired since the last verified time instant.
     *
     * @param lastVerified time instant when the entity was last verified
     * @return whether the trust has expired
     */
    default boolean isTrustedSinceLastVerified(Instant lastVerified) {
        int trustDuration = getTrustDuration().getLeft();
        TemporalUnit temporalUnit = getTrustDuration().getRight();
        if (trustDuration > 0) {
            Instant validTill = lastVerified.plus(trustDuration, temporalUnit);
            return validTill.isAfter(Instant.now());
        }
        // trusted by default
        return true;
    }
}
