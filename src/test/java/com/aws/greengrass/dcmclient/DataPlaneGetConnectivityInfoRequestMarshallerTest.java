package com.aws.greengrass.dcmclient;

import com.fasterxml.jackson.core.JsonFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.protocols.core.OperationInfo;
import software.amazon.awssdk.protocols.json.BaseAwsJsonProtocolFactory;
import software.amazon.awssdk.protocols.json.SdkJsonGenerator;
import software.amazon.awssdk.protocols.json.internal.marshall.JsonProtocolMarshallerBuilder;
import software.amazon.awssdk.services.greengrass.model.GetConnectivityInfoRequest;

import java.net.URI;
import java.net.URISyntaxException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
@ExtendWith({MockitoExtension.class})
public class DataPlaneGetConnectivityInfoRequestMarshallerTest {
    private static final String PATH = "greengrass/connectivityInfo/thing";
    private static final String THING_NAME = "thingName";
    private static final String PROTOCOL = "https";
    private static final String IP = "0.61.124.18";

    private DataPlaneGetConnectivityInfoRequestMarshaller dataPlaneGetConnectivityInfoRequestMarshaller;

    @Mock
    private BaseAwsJsonProtocolFactory baseAwsJsonProtocolFactory;

    @Test
    public void GIVEN_get_connectivity_request_WHEN_valid_THEN_marshall_request_returned() throws URISyntaxException {
        GetConnectivityInfoRequest getConnectivityInfoRequest = GetConnectivityInfoRequest.builder()
                .thingName("thingName")
                .overrideConfiguration(AwsRequestOverrideConfiguration.builder()
                        .credentialsProvider(DefaultCredentialsProvider.builder().build()).build())
                .build();

        OperationInfo operationInfo = OperationInfo.builder()
                .requestUri(String.format("/%s/%s", PATH, THING_NAME))
                .httpMethod(SdkHttpMethod.GET).hasExplicitPayloadMember(false).hasPayloadMembers(false).build();

        Mockito.doReturn(JsonProtocolMarshallerBuilder.create()
                .endpoint(new URI(String.format("%s://%s", PROTOCOL, IP)))
                .jsonGenerator(new SdkJsonGenerator(JsonFactory.builder()
                        .build(), "json"))
                .operationInfo(operationInfo).build())
                .when(baseAwsJsonProtocolFactory).createProtocolMarshaller(any());

        dataPlaneGetConnectivityInfoRequestMarshaller = new DataPlaneGetConnectivityInfoRequestMarshaller(baseAwsJsonProtocolFactory);
        SdkHttpFullRequest marshall = dataPlaneGetConnectivityInfoRequestMarshaller.marshall(getConnectivityInfoRequest);
        assertEquals(String.format("%s://%s/%s/%s", PROTOCOL, IP, PATH, THING_NAME), marshall.getUri().toString());
    }

    @Test
    public void GIVEN_get_connectivity_request_WHEN_invalid_THEN_throws_exception() throws URISyntaxException {

        OperationInfo operationInfo = OperationInfo.builder()
                .requestUri(String.format("/%s/%s", PATH, THING_NAME))
                .httpMethod(SdkHttpMethod.GET).hasExplicitPayloadMember(false).hasPayloadMembers(false).build();

        Mockito.lenient().doReturn(JsonProtocolMarshallerBuilder.create()
                .endpoint(new URI(String.format("%s://%s", PROTOCOL, IP)))
                .jsonGenerator(new SdkJsonGenerator(JsonFactory.builder()
                        .build(), "json"))
                .operationInfo(operationInfo).build())
                .when(baseAwsJsonProtocolFactory).createProtocolMarshaller(any());

        dataPlaneGetConnectivityInfoRequestMarshaller = new DataPlaneGetConnectivityInfoRequestMarshaller(baseAwsJsonProtocolFactory);
        NullPointerException ex = Assertions.assertThrows(NullPointerException.class,
                () -> dataPlaneGetConnectivityInfoRequestMarshaller.marshall(null));

        assertThat(ex.getMessage(), containsString("getConnectivityInfoRequest must not be null"));
    }
}
