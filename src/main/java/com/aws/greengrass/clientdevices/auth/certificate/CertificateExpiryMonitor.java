/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate;

import com.aws.greengrass.clientdevices.auth.certificate.infra.CertificateGeneratorRegistry;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import lombok.AccessLevel;
import lombok.Setter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class CertificateExpiryMonitor {
    private static final Logger LOGGER = LogManager.getLogger(CertificateExpiryMonitor.class);
    private static final long DEFAULT_CERT_EXPIRY_CHECK_SECONDS = 30;
    private final CertificateGeneratorRegistry certificateGeneratorRegistry;

    @Setter(AccessLevel.PACKAGE)  // for unit tests
    private Clock clock;

    private final ScheduledExecutorService ses;

    private final ConnectivityInformation connectivityInformation;

    private ScheduledFuture<?> monitorFuture;

    /**
     * Construct a new CertificateExpiryMonitor.
     *
     * @param ses                      ScheduledExecutorService to schedule cert expiry checks
     * @param connectivityInformation Connectivity Info Provider
     * @param clock                    clock
     * @param certificateGeneratorRegistry  a store of CertificateGenerators
     */
    @Inject
    public CertificateExpiryMonitor(ScheduledExecutorService ses,
                                    ConnectivityInformation connectivityInformation,
                                    Clock clock,
                                    CertificateGeneratorRegistry certificateGeneratorRegistry) {
        this.ses = ses;
        this.connectivityInformation = connectivityInformation;
        this.clock = clock;
        this.certificateGeneratorRegistry = certificateGeneratorRegistry;
    }

    /**
     * Start cert expiry monitor.
     */
    public void startMonitor() {
        startMonitor(Duration.ofSeconds(DEFAULT_CERT_EXPIRY_CHECK_SECONDS));
    }

    void startMonitor(Duration certExpiryCheck) {
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
        }
        monitorFuture = ses.scheduleAtFixedRate(this::watchForCertExpiryOnce, certExpiryCheck.toMillis(),
                certExpiryCheck.toMillis(), TimeUnit.MILLISECONDS);
    }

    void watchForCertExpiryOnce() {
        Set<CertificateGenerator> generators = certificateGeneratorRegistry.getCertificateGenerators();
        for (CertificateGenerator generator : generators) {
            new CertRotationDecider(generator, clock)
                    .rotationReady()
                    .ifPresent(reason -> {
                        try {
                            generator.generateCertificate(connectivityInformation::getCachedHostAddresses, reason);
                        } catch (CertificateGenerationException e) {
                            LOGGER.atError().cause(e).log(
                                    "Error generating certificate. Will be retried after {} seconds",
                                    DEFAULT_CERT_EXPIRY_CHECK_SECONDS);
                        }
                    });
        }
    }

    /**
     * Add cert to cert expiry monitor.
     *
     * @param cg certificate generator
     */
    public void addToMonitor(CertificateGenerator cg) {
        certificateGeneratorRegistry.registerGenerator(cg);
    }

    /**
     * Stop cert expiry monitor.
     */
    public void stopMonitor() {
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
        }
    }

    /**
     * Remove cert from cert expiry monitor.
     *
     * @param cg certificate generator
     */
    public void removeFromMonitor(CertificateGenerator cg) {
        certificateGeneratorRegistry.registerGenerator(cg);
    }

    private static class CertRotationDecider {

        private final Instant expiryTime;
        private final Instant currentTime;

        CertRotationDecider(CertificateGenerator cg, Clock clock) {
            this.expiryTime = cg.getExpiryTime();
            this.currentTime = Instant.now(clock);
        }

        /**
         * Determines if certificate rotation is ready.
         *
         * @return reason for cert rotation
         */
        public Optional<String> rotationReady() {
            if (isExpired()) {
                return Optional.of(String.format("certificate expired at %s", expiryTime));
            }

            if (isAboutToExpire()) {
                return Optional.of(String.format(
                        "certificate is approaching expiration at %s with %d seconds remaining",
                        expiryTime,
                        getValidity().getSeconds()
                ));
            }

            return Optional.empty();
        }

        private boolean isExpired() {
            return expiryTime.isBefore(currentTime);
        }

        private boolean isAboutToExpire() {
            return !isExpired() && expiryTime.isBefore(currentTime.plus(1, ChronoUnit.DAYS));
        }

        private Duration getValidity() {
            Duration duration = Duration.between(currentTime, expiryTime);
            return duration.isNegative() ? Duration.ZERO : duration;
        }
    }
}
