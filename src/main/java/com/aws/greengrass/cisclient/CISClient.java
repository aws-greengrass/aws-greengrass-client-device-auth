package com.aws.greengrass.cisclient;

import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.RetryUtils;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;
import software.amazon.awssdk.services.greengrassv2data.model.GetConnectivityInfoRequest;
import software.amazon.awssdk.services.greengrassv2data.model.GetConnectivityInfoResponse;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ResourceNotFoundException;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

/**
 * Client for retrieving connectivity info from CIS - Connectivity Info Service.
 */
public class CISClient {
    private static final Logger LOGGER = LogManager.getLogger(CISClient.class);
    private static final RetryUtils.RetryConfig GET_CONNECTIVITY_RETRY_CONFIG =
            RetryUtils.RetryConfig.builder().initialRetryInterval(Duration.ofMillis(100)).maxAttempt(3)
                    .retryableExceptions(Arrays.asList(ThrottlingException.class, InternalServerException.class))
                    .build();

    private final DeviceConfiguration deviceConfiguration;
    private final GreengrassV2DataClient greengrassV2DataClient;

    private List<ConnectivityInfo> cachedConnectivityInfo = Collections.emptyList();

    /**
     * Constructor.
     *
     * @param deviceConfiguration client to get the device details
     * @param clientFactory       factory to get data plane client
     */
    @Inject
    public CISClient(DeviceConfiguration deviceConfiguration, GreengrassServiceClientFactory clientFactory) {
        this.deviceConfiguration = deviceConfiguration;
        this.greengrassV2DataClient = clientFactory.getGreengrassV2DataClient();
    }

    /**
     * Get cached connectivity info.
     *
     * @return list of cached connectivity info items
     */
    public List<ConnectivityInfo> getCachedConnectivityInfo() {
        return cachedConnectivityInfo;
    }

    /**
     * Get connectivity info.
     *
     * @return list of connectivity info items
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public List<ConnectivityInfo> getConnectivityInfo() {
        GetConnectivityInfoRequest getConnectivityInfoRequest = GetConnectivityInfoRequest.builder()
                .thingName(Coerce.toString(deviceConfiguration.getThingName())).build();

        try {
            GetConnectivityInfoResponse getConnectivityInfoResponse = RetryUtils.runWithRetry(
                    GET_CONNECTIVITY_RETRY_CONFIG,
                    () -> greengrassV2DataClient.getConnectivityInfo(getConnectivityInfoRequest),
                    "get-connectivity",
                    LOGGER);
            if (getConnectivityInfoResponse.hasConnectivityInfo()) {
                cachedConnectivityInfo = getConnectivityInfoResponse.connectivityInfo();
            } else {
                cachedConnectivityInfo = Collections.emptyList();
            }
        } catch (InterruptedException e) {
            LOGGER.atError().cause(e).log("Get connectivity info got interrupted");
            // interrupt the current thread so that higher-level interrupt handlers can take care of it
            Thread.currentThread().interrupt();
            throw new CloudServiceInteractionException("Failed to get connectivity info, process got interrupted", e);
        } catch (ValidationException | ResourceNotFoundException e) {
            LOGGER.atWarn().cause(e).log("Connectivity info doesn't exist");
            cachedConnectivityInfo = Collections.emptyList();
        } catch (Exception e) {
            LOGGER.atError().cause(e).log("Failed to get connectivity info from cloud");
            throw new CloudServiceInteractionException("Failed to get connectivity info", e);
        }

        return cachedConnectivityInfo;
    }
}
