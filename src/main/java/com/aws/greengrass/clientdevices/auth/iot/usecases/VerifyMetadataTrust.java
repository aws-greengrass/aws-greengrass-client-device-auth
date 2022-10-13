/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS;

public class VerifyMetadataTrust implements UseCases.UseCase<Boolean, Instant> {
    private static final AtomicInteger clientDeviceTrustDurationHours =
            new AtomicInteger(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS);

    /**
     * Validates trust of a previously verified metadata.
     *
     * @param lastVerified last verified instant
     * @return boolean indicating whether the metadata can be trusted
     */
    @Override
    public Result<Boolean> apply(Instant lastVerified) {
        return Result.ok(isWithinTrustDuration(lastVerified));
    }

    /**
     * Updates the duration for which a client device metadata can be trusted.
     *
     * @param newTrustDuration desired trust duration in hours
     */
    public void updateClientDeviceTrustDurationHours(int newTrustDuration) {
        clientDeviceTrustDurationHours.set(newTrustDuration);
    }

    private boolean isWithinTrustDuration(Instant lastVerifiedInstant) {
        if (lastVerifiedInstant == null) {
            return false;
        }
        Instant validTill = lastVerifiedInstant.plus(clientDeviceTrustDurationHours.get(), ChronoUnit.HOURS);
        return validTill.isAfter(Instant.now());
    }
}
