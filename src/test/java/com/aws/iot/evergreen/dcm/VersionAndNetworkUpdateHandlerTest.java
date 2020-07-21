/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm;

import com.aws.iot.evergreen.testcommons.testutilities.EGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, EGExtension.class})
public class VersionAndNetworkUpdateHandlerTest extends EGExtension {

    @Mock
    ExecutorService executorService;

    VersionAndNetworkUpdateHandler handler;

    @BeforeEach
    public void setup() {
        handler = new VersionAndNetworkUpdateHandler(executorService);
    }

    @Test
    public void GIVEN_version_and_network_update_handler_WHEN_handle_update_THEN_executes_on_executor_service() {
        handler.handleServiceVersionUpdate(Constants.CIS_SERVICE_NAME);

        verify(executorService, times(1)).execute(any());
    }
}
