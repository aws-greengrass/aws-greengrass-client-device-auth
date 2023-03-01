/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Wrapper around {@link CDAConfiguration} so that configuration can
 * easily be injected throughout the component.
 */
public class CDAConfigurationReference extends AtomicReference<CDAConfiguration> {
    private static final long serialVersionUID = -5899407776271095275L;

    public CDAConfigurationReference(CDAConfiguration initialValue) {
        super(initialValue);
    }

    public CDAConfigurationReference() {
        super();
    }
}
