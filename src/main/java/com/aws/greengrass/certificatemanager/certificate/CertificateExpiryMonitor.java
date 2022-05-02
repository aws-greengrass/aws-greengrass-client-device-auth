/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.cisclient.ConnectivityInfoProvider;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.security.KeyStoreException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class CertificateExpiryMonitor {
    private static final Logger LOGGER = LogManager.getLogger(CertificateExpiryMonitor.class);
    private static final long DEFAULT_CERT_EXPIRY_CHECK_SECONDS = 30;

    private final ScheduledExecutorService ses;

    private final ConnectivityInfoProvider connectivityInfoProvider;

    private final Set<CertificateGenerator> monitoredCertificateGenerators = new CopyOnWriteArraySet<>();

    private ScheduledFuture<?> monitorFuture;

    /**
     * Constructor.
     * @param ses       ScheduledExecutorService to schedule cert expiry checks
     * @param connectivityInfoProvider Connectivity Info Provider
     */
    @Inject
    public CertificateExpiryMonitor(ScheduledExecutorService ses, ConnectivityInfoProvider connectivityInfoProvider) {
        this.ses = ses;
        this.connectivityInfoProvider = connectivityInfoProvider;
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
        for (CertificateGenerator cg : monitoredCertificateGenerators) {
            if (!cg.shouldRegenerate()) {
                continue;
            }
            try {
                cg.generateCertificate(connectivityInfoProvider::getCachedHostAddresses, "certificate has expired");
            } catch (KeyStoreException e) {
                LOGGER.atError().cause(e).log("Error generating certificate. Will be retried after {} seconds",
                        DEFAULT_CERT_EXPIRY_CHECK_SECONDS);
            }
        }
    }

    /**
     * Add cert to cert expiry monitor.
     * @param cg CertificateGenerator instance for the certificate
     */
    public void addToMonitor(CertificateGenerator cg) {
        monitoredCertificateGenerators.add(cg);
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
     * @param cg CertificateGenerator instance for the certificate
     */
    public void removeFromMonitor(CertificateGenerator cg) {
        monitoredCertificateGenerators.remove(cg);
    }
}
