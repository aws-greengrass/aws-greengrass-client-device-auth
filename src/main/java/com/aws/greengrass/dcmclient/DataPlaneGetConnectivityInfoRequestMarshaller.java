package com.aws.greengrass.dcmclient;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.runtime.transform.Marshaller;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.protocols.core.OperationInfo;
import software.amazon.awssdk.protocols.core.ProtocolMarshaller;
import software.amazon.awssdk.protocols.json.BaseAwsJsonProtocolFactory;
import software.amazon.awssdk.services.greengrass.model.GetConnectivityInfoRequest;
import software.amazon.awssdk.services.greengrass.model.GreengrassException;
import software.amazon.awssdk.utils.Validate;

public class DataPlaneGetConnectivityInfoRequestMarshaller implements Marshaller<GetConnectivityInfoRequest> {
    private static final OperationInfo SDK_OPERATION_BINDING;
    private final BaseAwsJsonProtocolFactory protocolFactory;

    public DataPlaneGetConnectivityInfoRequestMarshaller(BaseAwsJsonProtocolFactory protocolFactory) {
        this.protocolFactory = protocolFactory;
    }

    @Override
    public SdkHttpFullRequest marshall(GetConnectivityInfoRequest getConnectivityInfoRequest) {
        Validate.paramNotNull(getConnectivityInfoRequest, "getConnectivityInfoRequest");

        try {
            ProtocolMarshaller<SdkHttpFullRequest> protocolMarshaller = this.protocolFactory
                    .createProtocolMarshaller(SDK_OPERATION_BINDING);
            return protocolMarshaller.marshall(getConnectivityInfoRequest);
        } catch (GreengrassException e) {
            throw SdkClientException.builder()
                    .message("Unable to marshall request to JSON: " + e.getMessage()).cause(e).build();
        }
    }

    static {
        SDK_OPERATION_BINDING = OperationInfo.builder().requestUri("/greengrass/connectivityInfo/thing/{ThingName}")
                .httpMethod(SdkHttpMethod.GET).hasExplicitPayloadMember(false).hasPayloadMembers(false).build();
    }
}
