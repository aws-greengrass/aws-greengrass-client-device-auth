/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.cisclient;

import com.aws.iot.evergreen.deployment.exceptions.AWSIotException;
import com.aws.iot.evergreen.iot.IotCloudHelper;
import com.aws.iot.evergreen.iot.IotConnectionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;

public class CISClient {
    private static final String CIS_GET_CONNECTIVITY_INFO_PATH = "greengrass/connectivityInfo/thing";
    private static final String GET_CONNECTIVITY_INFO_VERB = "GET";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String endpoint;
    private final IotConnectionManager iotConnectionManager;
    private final IotCloudHelper iotCloudHelper;

    /**
     * Constructor to create a CIS Client.
     *
     * @param endpoint             host to talk to
     * @param iotConnectionManager See {@link IotConnectionManager}
     * @param iotCloudHelper       See {@link IotCloudHelper}
     */
    public CISClient(String endpoint, IotConnectionManager iotConnectionManager, IotCloudHelper iotCloudHelper) {
        this.endpoint = endpoint;
        this.iotConnectionManager = iotConnectionManager;
        this.iotCloudHelper = iotCloudHelper;
    }

    /**
     * Gets the Connectivity information for a thing.
     *
     * @param request Request with the thing name
     * @return response with connectivity information
     * @throws CISClientException if unable to get the connectivity info
     */
    public GetConnectivityInfoResponse getConnectivityInfo(@NonNull GetConnectivityInfoRequest request)
            throws CISClientException {
        String response;
        try {
            String url =
                    String.format("https://%s/%s/%s", endpoint, CIS_GET_CONNECTIVITY_INFO_PATH, request.getThingName());
            response = iotCloudHelper.sendHttpRequest(iotConnectionManager, url, GET_CONNECTIVITY_INFO_VERB, null);
        } catch (AWSIotException e) {
            throw new CISClientException("Failed to get connectivity info from CIS", e);
        }

        GetConnectivityInfoResponse getConnectivityInfoResponse;
        try {
            getConnectivityInfoResponse = OBJECT_MAPPER.readValue(response, GetConnectivityInfoResponse.class);
        } catch (JsonProcessingException e) {
            throw new CISClientException(String.format("Unparsable CIS response: %s", response), e);
        }

        if (getConnectivityInfoResponse.hasEmptyFields()) {
            throw new CISClientException(String.format("Bad CIS response: %s", response));
        }

        return getConnectivityInfoResponse;
    }
}
