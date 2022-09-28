/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.infra.NetworkState;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;

import java.util.Optional;
import javax.inject.Inject;

public class VerifyIotCertificate implements UseCases.UseCase<Exception, String> {
    private final NetworkState networkState;
    private final IotAuthClient iotAuthClient;


    /**
     * Register core certificate authority with Greengrass cloud.
     * @param networkState        Network state infrastructure
     * @param iotAuthClient       IoT dataplane API client
     */
    @Inject
    public VerifyIotCertificate(NetworkState networkState,
                                IotAuthClient iotAuthClient) {
        this.networkState = networkState;
        this.iotAuthClient = iotAuthClient;
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
    public Result apply(String certificatePem)  {
        // Only attempt to go to the cloud if we think GG has cloud connectivity
        if (networkState.getConnectionStateFromMqtt() == NetworkState.ConnectionState.NETWORK_UP) {
            Optional<String> certificateId = getCertificateIdFromIot(certificatePem);
            return Result.ok(certificateId.isPresent());
            //Certificate certificate = new Certificate(certificateId.get());
            // TODO: insert certificate into registry
        }

        // TODO: fetch from registry
        return Result.ok(false);
    }
}
