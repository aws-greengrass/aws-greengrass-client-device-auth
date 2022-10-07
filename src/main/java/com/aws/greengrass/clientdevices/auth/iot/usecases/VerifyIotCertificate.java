/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.infra.NetworkState;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;

import java.util.Optional;
import javax.inject.Inject;

public class VerifyIotCertificate implements UseCases.UseCase<Boolean, String> {
    private final IotAuthClient iotAuthClient;
    private final CertificateRegistry certificateRegistry;
    private final NetworkState networkState;


    /**
     * Verify a certificate with IoT Core.
     *
     * @param iotAuthClient       IoT auth client
     * @param certificateRegistry Certificate Registry
     * @param networkState        Network state
     */
    @Inject
    public VerifyIotCertificate(IotAuthClient iotAuthClient, CertificateRegistry certificateRegistry,
                                NetworkState networkState) {
        this.iotAuthClient = iotAuthClient;
        this.certificateRegistry = certificateRegistry;
        this.networkState = networkState;
    }

    @Override
    public Result<Boolean> apply(String certificatePem) {
        // If we think we have network connectivity, then opportunistically go to the
        // cloud for verification.
        // If the local registry doesn't have information about the certificate, or if
        // certificate information is outdated, then also go to the cloud, regardless
        // of whether we think we're connected.
        // Else, rely on whatever is in the local registry.
        boolean verified = false;

        try {
            Optional<Certificate> cert = certificateRegistry.getCertificateFromPem(certificatePem);
            if (cert.isPresent() && cert.get().isActive()) {
                verified = true;
            }

            if (networkState.getConnectionStateFromMqtt() == NetworkState.ConnectionState.NETWORK_UP || !verified) {
                cert = iotAuthClient.getIotCertificate(certificatePem);
                if (cert.isPresent()) {
                    verified = cert.get().isActive();
                }
            }

            if (!cert.get().isActive()) {
                // Certificate is not active - remove it
                certificateRegistry.deleteCertificate(cert.get());
            }
        } catch (InvalidCertificateException e) {
            return Result.ok(false);
        }

        return Result.ok(verified);
    }
}
