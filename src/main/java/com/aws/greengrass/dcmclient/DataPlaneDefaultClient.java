package com.aws.greengrass.dcmclient;

import software.amazon.awssdk.awscore.client.handler.AwsSyncClientHandler;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.RequestOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.client.handler.SyncClientHandler;
import software.amazon.awssdk.core.http.HttpResponseHandler;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.protocols.core.ExceptionMetadata;
import software.amazon.awssdk.protocols.json.AwsJsonProtocol;
import software.amazon.awssdk.protocols.json.AwsJsonProtocolFactory;
import software.amazon.awssdk.protocols.json.BaseAwsJsonProtocolFactory;
import software.amazon.awssdk.protocols.json.JsonOperationMetadata;
import software.amazon.awssdk.services.greengrass.model.BadRequestException;
import software.amazon.awssdk.services.greengrass.model.GreengrassException;
import software.amazon.awssdk.services.greengrass.model.InternalServerErrorException;

import java.util.Collections;
import java.util.List;

@SuppressWarnings({"PMD.UnusedPrivateField", "PMD.UnusedPrivateMethod"})
public class DataPlaneDefaultClient implements DataPlaneClient {

    private static final String PROTOCOL_VERSION = "1.1";
    private static final String ERROR_CODE_BAD_REQUEST = "BadRequestException";
    private static final String INTERNAL_SERVER_ERROR_EXCEPTION = "InternalServerErrorException";
    private static final String SERVICE_NAME = "greengrass";
    private static final String API_CALL = "ApiCall";

    private final SyncClientHandler clientHandler;
    private final AwsJsonProtocolFactory protocolFactory;
    private final SdkClientConfiguration clientConfiguration;

    protected DataPlaneDefaultClient(SdkClientConfiguration clientConfiguration) {
        this.clientHandler = new AwsSyncClientHandler(clientConfiguration);
        this.clientConfiguration = clientConfiguration;
        this.protocolFactory = this.init(AwsJsonProtocolFactory.builder()).build();
    }


   DataPlaneDefaultClient(SdkClientConfiguration clientConfiguration, SyncClientHandler clientHandler) {
        this.clientHandler = clientHandler;
       this.clientConfiguration = clientConfiguration;
       this.protocolFactory = this.init(AwsJsonProtocolFactory.builder()).build();
   }

   private <T extends BaseAwsJsonProtocolFactory.Builder<T>> T init(T builder) {
        return builder.clientConfiguration(this.clientConfiguration)
                .defaultServiceExceptionSupplier(GreengrassException::builder).protocol(AwsJsonProtocol.REST_JSON)
                .protocolVersion(PROTOCOL_VERSION).registerModeledException(ExceptionMetadata.builder()
                        .errorCode(ERROR_CODE_BAD_REQUEST).exceptionBuilderSupplier(BadRequestException::builder)
                        .httpStatusCode(400).build())
                .registerModeledException(ExceptionMetadata.builder().errorCode(INTERNAL_SERVER_ERROR_EXCEPTION)
                        .exceptionBuilderSupplier(InternalServerErrorException::builder).httpStatusCode(500).build());
    }

   private static List<MetricPublisher> resolveMetricPublishers(
            SdkClientConfiguration clientConfiguration, RequestOverrideConfiguration requestOverrideConfiguration) {
        List<MetricPublisher> publishers = null;
        if (requestOverrideConfiguration != null) {
            publishers = requestOverrideConfiguration.metricPublishers();
        }

        if (publishers == null || publishers.isEmpty()) {
            publishers = clientConfiguration.option(SdkClientOption.METRIC_PUBLISHERS);
        }

        if (publishers == null) {
            publishers = Collections.emptyList();
        }

        return publishers;
   }

    private HttpResponseHandler<AwsServiceException> createErrorResponseHandler(
            BaseAwsJsonProtocolFactory protocolFactory, JsonOperationMetadata operationMetadata) {
        return protocolFactory.createErrorResponseHandler(operationMetadata);
    }

    @Override
    public final String serviceName() {
        return SERVICE_NAME;
    }

    @Override
    public void close() {

    }
}
