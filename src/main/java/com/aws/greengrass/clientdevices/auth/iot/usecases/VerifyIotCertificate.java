/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.Optional;
import javax.inject.Inject;

public class VerifyIotCertificate implements UseCases.UseCase<Boolean, String> {
    private static final Logger logger = LogManager.getLogger(VerifyIotCertificate.class);

    private static final String VERIFICATION_SOURCE = "verificationSource";
    private static final String LOCAL_VERIFICATION_SOURCE = "local";
    private static final String CLOUD_VERIFICATION_SOURCE = "cloud";

    private final IotAuthClient iotAuthClient;
    private final CertificateRegistry certificateRegistry;
    private final NetworkStateProvider networkState;


    /**
     * Verify a certificate with IoT Core.
     *
     * @param iotAuthClient       IoT auth client
     * @param certificateRegistry Certificate Registry
     * @param networkState        Network state
     */
    @Inject
    public VerifyIotCertificate(IotAuthClient iotAuthClient, CertificateRegistry certificateRegistry,
                                NetworkStateProvider networkState) {
        this.iotAuthClient = iotAuthClient;
        this.certificateRegistry = certificateRegistry;
        this.networkState = networkState;
    }

    @Override
    public Boolean apply(String certificatePem) {
        // If we think we have network connectivity, then opportunistically go to the
        // cloud for verification.
        // If the local registry doesn't have information about the certificate, or if
        // certificate information is outdated, then also go to the cloud, regardless
        // of whether we think we're connected. It may seem a bit odd to attempt when we
        // don't think we're online, but we don't 100% trust our network state heuristic,
        // so this guarantees that we at least try once.
        // Else, rely on whatever is in the local registry.
        Optional<Certificate> cloudCert = Optional.empty();
        Certificate cert;

        try {
            cert = certificateRegistry.getOrCreateCertificate(certificatePem);
            if (!cert.isActive() || isNetworkUp()) {
                cloudCert = iotAuthClient.getIotCertificate(certificatePem);
            }
        } catch (InvalidCertificateException e) {
            logger.atWarn().kv("certificatePem", certificatePem).log("Unable to process certificate", e);
            return false;
        }

        // Information from the cloud is authoritative - update local registry if it is available
        if (cloudCert.isPresent()) {
            cert = cloudCert.get();
            if (cert.isActive()) {
                certificateRegistry.updateCertificate(cloudCert.get());
            } else {
                certificateRegistry.deleteCertificate(cloudCert.get());
            }
        }

        String verificationSource = cloudCert.isPresent() ? CLOUD_VERIFICATION_SOURCE : LOCAL_VERIFICATION_SOURCE;
        logger.atDebug().kv("certificateId", cert.getCertificateId()).kv(VERIFICATION_SOURCE, verificationSource)
                .log(cert.isActive() ? "Certificate is active" : "Certificate is not active");

        return cert.isActive();
    }

    private boolean isNetworkUp() {
        return networkState.getConnectionState() == NetworkStateProvider.ConnectionState.NETWORK_UP;
    }
}
