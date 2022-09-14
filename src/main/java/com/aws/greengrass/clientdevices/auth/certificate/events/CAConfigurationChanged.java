/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.events;

import com.aws.greengrass.clientdevices.auth.api.DomainEvent;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class CAConfigurationChanged implements DomainEvent {
    @Getter
    private CDAConfiguration configuration;
}
