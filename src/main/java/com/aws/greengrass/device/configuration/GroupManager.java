/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration;

import com.aws.greengrass.device.Session;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * a singleton manager class for managing device group roles.
 * It listens to configuration update through nucleus, On the hand, for each request in a session, it iterate through
 * the configurations to find match group(s), returning the authorization policies of group(s).
 */
@SuppressWarnings("PMD.UnusedPrivateField")
public class GroupManager {
    private static final Logger logger = LogManager.getLogger(GroupManager.class);

    private AtomicReference<GroupConfiguration> groupConfigurationRef;

    public void setGroupConfiguration(GroupConfiguration groupConfiguration) {
        groupConfigurationRef.set(groupConfiguration);
    }

    /**
     * find applicable policies to evaluate for the given device request.
     *
     * @param session session used to retrieve cached device attributes
     * @return set of applicable policy for the device
     */
    public Set<AuthorizationPolicy> getApplicablePolicies(Session session) {
        // GroupConfiguration config = groupConfiguration.get();
        //TODO iterate groups to find matching group(s), return the policies.

        return Collections.emptySet();
    }

}
