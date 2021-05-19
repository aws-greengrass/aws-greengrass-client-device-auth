package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.cisclient.CISClient;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class CertificateExpiryMonitor {
    private static final Logger LOGGER = LogManager.getLogger(CertificateExpiryMonitor.class);
    private static final long DEFAULT_CERT_EXPIRY_CHECK_SECONDS = 30;
    private static final int QUEUE_INITIAL_CAPACITY = 11;

    private final ScheduledExecutorService ses;

    private final CISClient cisClient;

    private final Queue<CertificateGenerator> monitoredCertificateGenerators = new
            PriorityBlockingQueue<>(QUEUE_INITIAL_CAPACITY, Comparator.comparing(CertificateGenerator::getExpiryTime));

    private ScheduledFuture<?> monitorFuture;

    /**
     * Constructor.
     * @param ses       ScheduledExecutorService to schedule cert expiry checks
     * @param cisClient CIS Client
     */
    @Inject
    public CertificateExpiryMonitor(ScheduledExecutorService ses, CISClient cisClient) {
        this.ses = ses;
        this.cisClient = cisClient;
    }

    /**
     * Start cert expiry monitor.
     */
    public void startMonitor() {
        startMonitor(DEFAULT_CERT_EXPIRY_CHECK_SECONDS);
    }

    void startMonitor(long certExpiryCheckSeconds) {
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
        }
        monitorFuture = ses.scheduleAtFixedRate(this::watchForCertExpiryOnce, certExpiryCheckSeconds,
                certExpiryCheckSeconds, TimeUnit.SECONDS);
    }

    private void watchForCertExpiryOnce() {
        List<CertificateGenerator> triedCertificateGenerators = new ArrayList<>();

        for (CertificateGenerator cg = monitoredCertificateGenerators.peek(); cg != null;
             cg = monitoredCertificateGenerators.peek()) {
            if (!cg.shouldRegenerate()) {
                break;
            }
            try {
                cg.generateCertificate(cisClient::getCachedConnectivityInfo);
            } catch (KeyStoreException e) {
                LOGGER.atError().cause(e).log("Error generating certificate. Will be retried after {} seconds",
                        DEFAULT_CERT_EXPIRY_CHECK_SECONDS);
            }
            triedCertificateGenerators.add(cg);
            monitoredCertificateGenerators.poll();
        }

        monitoredCertificateGenerators.addAll(triedCertificateGenerators);
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
}
