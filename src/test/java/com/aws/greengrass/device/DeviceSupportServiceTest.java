/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.dependency.State;
import com.aws.greengrass.device.configuration.ConfigurationFormatVersion;
import com.aws.greengrass.device.configuration.GroupConfiguration;
import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.device.configuration.Permission;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.hamcrest.collection.IsMapContaining;
import org.hamcrest.collection.IsMapWithSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class DeviceSupportServiceTest {

    private Kernel kernel;

    @TempDir
    Path rootDir;

    @Mock
    private GroupManager groupManager;

    @Mock
    private SessionManager sessionManager;

    @Captor
    ArgumentCaptor<GroupConfiguration> configurationCaptor;

    private void startNucleusWithConfig(String configFile, State expectedState) throws InterruptedException {
        CountDownLatch deviceSupportRunning = new CountDownLatch(1);
        kernel = new Kernel();
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFile).toString());
        kernel.getContext().addGlobalStateChangeListener((service, was, newState) -> {
            if (DeviceSupportService.DEVICE_SUPPORT_SERVICE_NAME.equals(service.getName()) && service.getState()
                    .equals(expectedState)) {
                deviceSupportRunning.countDown();
            }
        });
        kernel.getContext().put(GroupManager.class, groupManager);
        kernel.getContext().put(SessionManager.class, sessionManager);
        kernel.launch();
        assertThat(deviceSupportRunning.await(10, TimeUnit.SECONDS), is(true));
    }

    @AfterEach
    void cleanup() {
        kernel.shutdown();
    }

    @Test
    void GIVEN_no_group_configuration_WHEN_start_service_change_THEN_empty_configuration_model_instantiated()
            throws InterruptedException {
        startNucleusWithConfig("emptyGroupConfig.yaml", State.RUNNING);

        verify(groupManager).setGroupConfiguration(configurationCaptor.capture());
        GroupConfiguration groupConfiguration = configurationCaptor.getValue();
        assertThat(groupConfiguration.getGroups(), IsMapWithSize.anEmptyMap());
        assertThat(groupConfiguration.getPolicies(), IsMapWithSize.anEmptyMap());
    }

    @Test
    void GIVEN_bad_group_configuration_WHEN_start_service_THEN_no_configuration_update(ExtensionContext context)
            throws InterruptedException {
        ignoreExceptionOfType(context, IllegalArgumentException.class);

        startNucleusWithConfig("badGroupConfig.yaml", State.RUNNING);

        verify(groupManager, never()).setGroupConfiguration(any());
    }

    @Test
    void GIVEN_valid_group_configuration_WHEN_start_service_THEN_instantiated_configuration_model_updated()
            throws InterruptedException {
        startNucleusWithConfig("groupConfig.yaml", State.RUNNING);

        verify(groupManager).setGroupConfiguration(configurationCaptor.capture());
        GroupConfiguration groupConfiguration = configurationCaptor.getValue();
        assertThat(groupConfiguration.getVersion(), is(ConfigurationFormatVersion.MAR_05_2021));
        assertThat(groupConfiguration.getGroups(), IsMapWithSize.aMapWithSize(2));
        assertThat(groupConfiguration.getPolicies(), IsMapWithSize.aMapWithSize(1));
        assertThat(groupConfiguration.getGroups(), IsMapContaining
                .hasEntry(is("myTemperatureSensors"), hasProperty("policyName", is("sensorAccessPolicy"))));
        assertThat(groupConfiguration.getGroups(),
                IsMapContaining.hasEntry(is("myHumiditySensors"), hasProperty("policyName", is("sensorAccessPolicy"))));
        assertThat(groupConfiguration.getPolicies(), IsMapContaining.hasEntry(is("sensorAccessPolicy"),
                allOf(IsMapContaining.hasKey("policyStatement1"), IsMapContaining.hasKey("policyStatement2"))));

        Map<String, Set<Permission>> permissionMap = groupConfiguration.getGroupToPermissionsMap();
        assertThat(permissionMap, IsMapWithSize.aMapWithSize(2));

        Permission[] tempSensorPermissions =
                {Permission.builder().principal("myTemperatureSensors").operation("mqtt" + ":connect")
                        .resource("mqtt:clientId:foo").build(),
                        Permission.builder().principal("myTemperatureSensors").operation("mqtt:publish")
                                .resource("mqtt:topic:temperature").build(),
                        Permission.builder().principal("myTemperatureSensors").operation("mqtt:publish")
                                .resource("mqtt:topic:humidity").build()};
        assertThat(permissionMap.get("myTemperatureSensors"), containsInAnyOrder(tempSensorPermissions));
        Permission[] humidSensorPermissions =
                {Permission.builder().principal("myHumiditySensors").operation("mqtt:connect")
                        .resource("mqtt:clientId:foo").build(),
                        Permission.builder().principal("myHumiditySensors").operation("mqtt:publish")
                                .resource("mqtt:topic:temperature").build(),
                        Permission.builder().principal("myHumiditySensors").operation("mqtt:publish")
                                .resource("mqtt:topic:humidity").build()};
        assertThat(permissionMap.get("myHumiditySensors"), containsInAnyOrder(humidSensorPermissions));
    }

    @Test
    void GIVEN_group_has_no_policy_WHEN_start_service_THEN_no_configuration_update(ExtensionContext context)
            throws InterruptedException {
        ignoreExceptionOfType(context, IllegalArgumentException.class);

        startNucleusWithConfig("noGroupPolicyConfig.yaml", State.RUNNING);

        verify(groupManager, never()).setGroupConfiguration(any());
    }
}
