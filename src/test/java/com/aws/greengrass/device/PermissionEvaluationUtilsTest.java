/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.configuration.Permission;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PermissionEvaluationUtilsTest {

    @Test
    void GIVEN_single_group_permission_WHEN_evaluate_operation_permission_THEN_return_decision() {
        Map<String, Set<Permission>> groupPermissions = prepareGroupPermissionsData();
        boolean authorized = PermissionEvaluationUtils.isAuthorize("mqtt:publish", "mqtt:topic:a", groupPermissions);
        assertThat(authorized, is(true));

        authorized = PermissionEvaluationUtils.isAuthorize("mqtt:publish", "mqtt:topic:b", groupPermissions);
        assertThat(authorized, is(true));

        authorized = PermissionEvaluationUtils.isAuthorize("mqtt:subscribe", "mqtt:topic:b", groupPermissions);
        assertThat(authorized, is(true));

        authorized = PermissionEvaluationUtils.isAuthorize("mqtt:subscribe", "mqtt:topic:$foo/bar/+/baz",
                groupPermissions);
        assertThat(authorized, is(true));

        authorized = PermissionEvaluationUtils.isAuthorize("mqtt:connect", "mqtt:broker:localBroker", groupPermissions);
        assertThat(authorized, is(true));

        authorized = PermissionEvaluationUtils.isAuthorize("dds:connect", null, groupPermissions);
        assertThat(authorized, is(true));

        authorized = PermissionEvaluationUtils.isAuthorize("mqtt:publish", "mqtt:topic:d", groupPermissions);
        assertThat(authorized, is(false));

        authorized = PermissionEvaluationUtils.isAuthorize("mqtt:subscribe", "mqtt:message:a", groupPermissions);
        assertThat(authorized, is(false));
    }

    private Map<String, Set<Permission>> prepareGroupPermissionsData() {
        Permission[] sensorPermission = {
                Permission.builder().principal("sensor").operation("mqtt:publish").resource("mqtt:topic:a").build(),
                Permission.builder().principal("sensor").operation("mqtt:*").resource("mqtt:topic:b").build(),
                Permission.builder().principal("sensor").operation("mqtt:subscribe").resource("mqtt:topic:*").build(),
                Permission.builder().principal("sensor").operation("mqtt:connect").resource("*").build(),
                Permission.builder().principal("sensor").operation("dds:connect").build()
        };
        return Collections.singletonMap("sensor", new HashSet<>(Arrays.asList(sensorPermission)));
    }

}
