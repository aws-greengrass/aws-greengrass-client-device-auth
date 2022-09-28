/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.infra.NetworkState;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.registry.CertificateRegistry;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.time.Instant;
import java.util.Optional;
import javax.inject.Inject;

public class VerifyIotCertificate implements UseCases.UseCase<Exception, String> {
    private static final Logger logger = LogManager.getLogger(VerifyIotCertificate.class);

    private final NetworkState networkState;
    private final IotAuthClient iotAuthClient;
    private final CertificateRegistry certificateRegistry;


    /**
     * Register core certificate authority with Greengrass cloud.
     *
     * @param networkState        Network state infrastructure
     * @param iotAuthClient       IoT dataplane API client
     * @param certificateRegistry cert registry
     */
    @Inject
    public VerifyIotCertificate(NetworkState networkState, IotAuthClient iotAuthClient,
                                CertificateRegistry certificateRegistry) {
        this.networkState = networkState;
        this.iotAuthClient = iotAuthClient;
        this.certificateRegistry = certificateRegistry;
    }

    // TODO: We need to be able to differentiate between a failure due
    //  to cloud connectivity issues, and failures due to the certificate
    //  not being active. The latter should result in us revoking the cert
    //  from the registry, while the former means we should just fall back
    //  to whatever is currently in the registry.
    private Optional<String> getCertificateIdFromIot(String certificatePem) {
        Optional<String> certificateId = Optional.empty();
        try {
            certificateId = iotAuthClient.getActiveCertificateId(certificatePem);
        } catch (CloudServiceInteractionException e) {
            // Do nothing
        }
        return certificateId;
    }

    @Override
    public Result apply(String certificatePem) {
        // If we think we are online, the certificate is not ACTIVE, or if certificate
        // status information is stale, then go to the cloud.
        // This ensures that we eventually go to IoT Core even if there are shenanigans
        // going on where we have HTTP connectivity but MQTT is disconnected.
        String source = "local";
        Certificate cert = certificateRegistry.getCertificateByPem(certificatePem);
        if (networkState.getConnectionStateFromMqtt() == NetworkState.ConnectionState.NETWORK_UP
                || cert.getStatus() != Certificate.Status.ACTIVE
                || !isTimestampRecent(cert)) {
            Optional<String> certificateId = getCertificateIdFromIot(certificatePem);
            Certificate.Status newStatus = Certificate.Status.INACTIVE;
            if (certificateId.isPresent()) {
                newStatus = Certificate.Status.ACTIVE;
            }
            cert.updateStatus(newStatus);
            certificateRegistry.updateCertificate(cert);
            source = "cloud";
        }

        logger.atDebug().kv("CertificateID", cert.getIotCertificateId())
                .kv("status", cert.getStatus())
                .kv("source", source)
                .log("Verifying client device certificate");
        return Result.ok(cert.getStatus() == Certificate.Status.ACTIVE);
    }

    private boolean isTimestampRecent(Certificate certificate) {
        // TODO: Make this time configurable
        return certificate.getLastUpdated().isAfter(Instant.now().minusSeconds(60 * 60 * 24));
    }
}
