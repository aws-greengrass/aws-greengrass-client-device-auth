package com.aws.greengrass.dcmclient;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.greengrass.model.GetConnectivityInfoRequest;
import software.amazon.awssdk.services.greengrass.model.GetConnectivityInfoResponse;

public interface DataPlaneClient extends SdkClient {
    /*
    * All of this code is taken from the aws sdk. This was required because of the different
    * URL for data plane and control api. Unfortunately we cannot set this URL in sdk neither can we
    * extend these classes. So the only option was to use the same code with different api URL .
    * Control Plane URL: "/greengrass/things/{ThingName}/connectivityInfo" HTTP PUT
    * Data Plane URL: "/greengrass/connectivityInfo/thing/{ThingName}". HTTP POST
    * */
    static DataPlaneClientDefaultBuilder builder() {
        return new DataPlaneClientDefaultBuilder();
    }

    @SuppressWarnings("PMD.AvoidUncheckedExceptionsInSignatures")
    default GetConnectivityInfoResponse getConnectivityInfo(GetConnectivityInfoRequest getConnectivityInfoRequest)
            throws AwsServiceException, SdkClientException {
        throw new UnsupportedOperationException();
    }
}
