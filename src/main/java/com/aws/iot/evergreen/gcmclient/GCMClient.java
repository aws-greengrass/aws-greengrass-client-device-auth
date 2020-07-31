/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.gcmclient;

import com.aws.iot.evergreen.deployment.exceptions.AWSIotException;
import com.aws.iot.evergreen.iot.IotCloudHelper;
import com.aws.iot.evergreen.iot.IotConnectionManager;
import com.aws.iot.evergreen.iot.model.IotCloudResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A client to talk to Greengrass Certificate Manager cloud service We need only one data plane operation.
 * 'GetCertificate' This is has been ported over from https://code.amazon.com/packages/GoAmzn-GreengrassCertificateManagementServiceClient
 * If any other component needs to use this Client, consider moving this out in its own pkg.
 */
public class GCMClient {
    private static final String GCM_GET_CERTIFICATE_PATH = "greengrass/gcm/certificate";
    private static final String GET_CERTIFICATE_HTTP_VERB = "POST";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String endpoint;
    private final IotConnectionManager iotConnectionManager;
    private final IotCloudHelper iotCloudHelper;

    /**
     * Constructor to create a GCM Client.
     * @param endpoint host to talk to
     * @param iotConnectionManager See {@link IotConnectionManager}
     * @param iotCloudHelper See {@link IotCloudHelper}
     */
    public GCMClient(String endpoint, IotConnectionManager iotConnectionManager, IotCloudHelper iotCloudHelper) {
        this.endpoint = endpoint;
        this.iotConnectionManager = iotConnectionManager;
        this.iotCloudHelper = iotCloudHelper;
    }

    /**
     * Gets the Certificate for the provided GetCertificateRequest.
     *
     * @param request A CSR (Certificate signing request)
     * @return Certificate response
     * @throws GCMClientException if unable to get the cert
     */
    public GetCertificateResponse getCertificate(GetCertificateRequest request) throws GCMClientException {
        byte[] bytes;
        try {
            bytes = OBJECT_MAPPER.writeValueAsBytes(request);
        } catch (JsonProcessingException e) {
            throw new GCMClientException("Cannot marshall the request into json", e);
        }

        String response;
        try {
            String url = String.format("https://%s/%s", endpoint, GCM_GET_CERTIFICATE_PATH);
            IotCloudResponse cloudResponse = iotCloudHelper.sendHttpRequest(
                    iotConnectionManager, url, GET_CERTIFICATE_HTTP_VERB, bytes);
            response = cloudResponse.toString();
        } catch (AWSIotException e) {
            throw new GCMClientException("Failed to get certificate from GCM", e);
        }

        GetCertificateResponse getCertificateResponse;
        try {
            getCertificateResponse = OBJECT_MAPPER.readValue(response, GetCertificateResponse.class);
        } catch (JsonProcessingException e) {
            throw new GCMClientException(String.format("Unparsable GCM response: %s", response), e);
        }

        if (getCertificateResponse.hasEmptyFields()) {
            throw new GCMClientException(
                    String.format("Bad GCM response: %s", getCertificateResponse.getCertificate()));
        }

        return getCertificateResponse;
    }
}
