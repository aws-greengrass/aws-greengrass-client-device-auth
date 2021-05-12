/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.certificatemanager.certificate.CertificateHelper;
import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topic;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.device.configuration.ConfigurationFormatVersion;
import com.aws.greengrass.device.configuration.GroupConfiguration;
import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.device.configuration.Permission;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.lifecyclemanager.exceptions.ServiceLoadException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.hamcrest.collection.IsMapContaining;
import org.hamcrest.collection.IsMapWithSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.model.PutCertificateAuthoritiesRequest;
import software.amazon.awssdk.services.greengrassv2data.model.ResourceNotFoundException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class ClientDevicesAuthServiceTest {
    private static final long TEST_TIME_OUT_SEC = 30L;

    private Kernel kernel;

    @TempDir
    Path rootDir;

    @Mock
    private GroupManager groupManager;

    @Mock
    private GreengrassServiceClientFactory clientFactory;

    @Mock
    private GreengrassV2DataClient client;

    @Captor
    private ArgumentCaptor<GroupConfiguration> configurationCaptor;

    @Captor
    private ArgumentCaptor<PutCertificateAuthoritiesRequest> putCARequestArgumentCaptor;


    @BeforeEach
    void setup() {
        kernel = new Kernel();
        kernel.getContext().put(GroupManager.class, groupManager);
        kernel.getContext().put(GreengrassServiceClientFactory.class, clientFactory);

        lenient().when(clientFactory.getGreengrassV2DataClient()).thenReturn(client);
    }

    @AfterEach
    void cleanup() {
        kernel.shutdown();
    }

    private void startNucleusWithConfig(String configFileName) throws InterruptedException {
        startNucleusWithConfig(configFileName, State.RUNNING);
    }


    private void startNucleusWithConfig(String configFileName, State expectedServiceState) throws InterruptedException {
        CountDownLatch authServiceRunning = new CountDownLatch(1);
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        kernel.getContext().addGlobalStateChangeListener((service, was, newState) -> {
            if (ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME.equals(service.getName()) && service.getState()
                    .equals(expectedServiceState)) {
                authServiceRunning.countDown();
            }
        });
        kernel.launch();
        assertThat(authServiceRunning.await(TEST_TIME_OUT_SEC, TimeUnit.SECONDS), is(true));
    }

    @Test
    void GIVEN_no_group_configuration_WHEN_start_service_change_THEN_empty_configuration_model_instantiated()
            throws Exception {
        startNucleusWithConfig("emptyGroupConfig.yaml");

        verify(groupManager).setGroupConfiguration(configurationCaptor.capture());
        GroupConfiguration groupConfiguration = configurationCaptor.getValue();
        assertThat(groupConfiguration.getDefinitions(), IsMapWithSize.anEmptyMap());
        assertThat(groupConfiguration.getPolicies(), IsMapWithSize.anEmptyMap());
    }

    @Test
    void GIVEN_bad_group_configuration_WHEN_start_service_THEN_service_in_error_state(ExtensionContext context)
            throws Exception {
        ignoreExceptionOfType(context, IllegalArgumentException.class);
        ignoreExceptionOfType(context, UnrecognizedPropertyException.class);

        startNucleusWithConfig("badGroupConfig.yaml", State.ERRORED);

        verify(groupManager, never()).setGroupConfiguration(any());
    }

    @Test
    void GIVEN_valid_group_configuration_WHEN_start_service_THEN_instantiated_configuration_model_updated()
            throws Exception {
        startNucleusWithConfig("config.yaml");

        verify(groupManager).setGroupConfiguration(configurationCaptor.capture());
        GroupConfiguration groupConfiguration = configurationCaptor.getValue();
        assertThat(groupConfiguration.getFormatVersion(), is(ConfigurationFormatVersion.MAR_05_2021));
        assertThat(groupConfiguration.getDefinitions(), IsMapWithSize.aMapWithSize(2));
        assertThat(groupConfiguration.getPolicies(), IsMapWithSize.aMapWithSize(1));
        assertThat(groupConfiguration.getDefinitions(), IsMapContaining
                .hasEntry(is("myTemperatureSensors"), hasProperty("policyName", is("sensorAccessPolicy"))));
        assertThat(groupConfiguration.getDefinitions(),
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
            throws Exception {
        ignoreExceptionOfType(context, IllegalArgumentException.class);
        ignoreExceptionOfType(context, AuthorizationException.class);

        startNucleusWithConfig("noGroupPolicyConfig.yaml", State.ERRORED);

        verify(groupManager, never()).setGroupConfiguration(any());
    }

    @Test
    void GIVEN_GG_with_cda_WHEN_subscribing_to_ca_updates_THEN_get_list_of_certs() throws Exception {
        startNucleusWithConfig("config.yaml");
        CountDownLatch countDownLatch = new CountDownLatch(1);

        kernel.findServiceTopic(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME)
                .lookup("runtime", "certificates", "authorities").subscribe((why, newv) -> {
            List<String> caPemList = (List<String>) newv.toPOJO();
            if (caPemList != null) {
                Assertions.assertEquals(1, caPemList.size());
                countDownLatch.countDown();
            }
        });
        Assertions.assertTrue(countDownLatch.await(TEST_TIME_OUT_SEC, TimeUnit.SECONDS));
    }

    @Test
    void GIVEN_updated_ca_certs_WHEN_updateCACertificateConfig_THEN_cert_topic_updated()
            throws InterruptedException, ServiceLoadException, IOException {
        startNucleusWithConfig("config.yaml");

        ClientDevicesAuthService clientDevicesAuthService =
                (ClientDevicesAuthService) kernel.locate(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME);

        List<String> expectedCACerts = new ArrayList<>(Arrays.asList("CA1"));
        clientDevicesAuthService.updateCACertificateConfig(expectedCACerts);
        assertCaCertTopicContains(expectedCACerts);

        expectedCACerts.add("CA2");
        clientDevicesAuthService.updateCACertificateConfig(expectedCACerts);
        assertCaCertTopicContains(expectedCACerts);

        expectedCACerts.remove("CA1");
        expectedCACerts.add("CA3");
        clientDevicesAuthService.updateCACertificateConfig(expectedCACerts);
        assertCaCertTopicContains(expectedCACerts);
    }

    void assertCaCertTopicContains(List<String> expectedCerts) {
        Topic caCertTopic = kernel.findServiceTopic(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME)
                .lookup("runtime", "certificates", "authorities");
        List<String> caPemList = (List<String>) caCertTopic.toPOJO();
        Assertions.assertNotNull(caPemList);
        assertThat(caPemList, IsIterableContainingInAnyOrder.containsInAnyOrder(expectedCerts.toArray()));
    }

    @Test
    void GIVEN_GG_with_cda_WHEN_restart_kernel_THEN_ca_is_persisted()
            throws InterruptedException, CertificateEncodingException, KeyStoreException, IOException,
            ServiceLoadException {
        startNucleusWithConfig("config.yaml");

        String initialPassphrase = getCaPassphrase();
        Assertions.assertNotNull(initialPassphrase);
        List<String> initialCerts = getCaCertificates();
        assertThat(initialCerts, is(not(empty())));

        kernel.shutdown();
        kernel = new Kernel().parseArgs("-r", rootDir.toAbsolutePath().toString());
        kernel.getContext().put(GreengrassServiceClientFactory.class, clientFactory);
        startNucleusWithConfig("config.yaml");

        String finalPassphrase = getCaPassphrase();
        Assertions.assertNotNull(finalPassphrase);
        List<String> finalCerts = getCaCertificates();
        assertThat(finalCerts, is(not(empty())));

        assertThat(initialPassphrase, is(finalPassphrase));
        assertThat(initialCerts, is(finalCerts));
    }

    private String getCaPassphrase() {
        Topic caPassphraseTopic = kernel.findServiceTopic(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME)
                .lookup("runtime", "ca_passphrase");
        return (String) caPassphraseTopic.toPOJO();
    }

    private List<String> getCaCertificates()
            throws ServiceLoadException, CertificateEncodingException, KeyStoreException, IOException {
        ClientDevicesAuthService clientDevicesAuthService =
                (ClientDevicesAuthService) kernel.locate(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME);
        return clientDevicesAuthService.getCertificateManager().getCACertificates();
    }


    @Test
    void GIVEN_GG_with_cda_WHEN_updated_ca_type_THEN_ca_is_updated()
            throws InterruptedException, ServiceLoadException, KeyStoreException, CertificateException, IOException {
        startNucleusWithConfig("config.yaml");

        List<String> initialCACerts = getCaCertificates();
        X509Certificate initialCA = pemToX509Certificate(initialCACerts.get(0));
        assertThat(initialCA.getSigAlgName(), is(CertificateHelper.RSA_SIGNING_ALGORITHM));
        String initialCaPassPhrase = getCaPassphrase();

        kernel.locate(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME).getConfig()
                .find(KernelConfigResolver.CONFIGURATION_CONFIG_KEY,
                        ClientDevicesAuthService.CA_TYPE_TOPIC).withValue(Collections.singletonList("RSA_2048"));
        // Block until subscriber has finished updating
        kernel.getContext().waitForPublishQueueToClear();

        List<String> secondCACerts = getCaCertificates();
        X509Certificate secondCA = pemToX509Certificate(secondCACerts.get(0));
        assertThat(secondCA.getSigAlgName(), is(CertificateHelper.RSA_SIGNING_ALGORITHM));
        assertThat(initialCA, is(secondCA));
        assertThat(getCaPassphrase(), is(initialCaPassPhrase));

        kernel.locate(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME).getConfig()
                .find(KernelConfigResolver.CONFIGURATION_CONFIG_KEY,
                        ClientDevicesAuthService.CA_TYPE_TOPIC).withValue(Collections.singletonList("ECDSA_P256"));
        // Block until subscriber has finished updating
        kernel.getContext().waitForPublishQueueToClear();

        List<String> thirdCACerts = getCaCertificates();
        X509Certificate thirdCA = pemToX509Certificate(thirdCACerts.get(0));
        assertThat(thirdCA.getSigAlgName(), is(CertificateHelper.ECDSA_SIGNING_ALGORITHM));
        assertThat(initialCA, not(thirdCA));
        assertThat(getCaPassphrase(), not(initialCaPassPhrase));

        verify(client, times(3)).putCertificateAuthorities(putCARequestArgumentCaptor.capture());
        List<List<String>> certificatesInRequests =
                putCARequestArgumentCaptor.getAllValues().stream().map(
                        PutCertificateAuthoritiesRequest::coreDeviceCertificates).collect(
                        Collectors.toList());
        assertThat(certificatesInRequests, contains(initialCACerts, secondCACerts, thirdCACerts));
    }

    @Test
    void GIVEN_GG_with_cda_WHEN_ca_type_provided_in_config_THEN_valid_ca_created()
            throws IOException, InterruptedException, ServiceLoadException, CertificateException, KeyStoreException {
        startNucleusWithConfig("config_with_ec_ca.yaml");

        List<String> initialCACerts = getCaCertificates();
        X509Certificate initialCA = pemToX509Certificate(initialCACerts.get(0));
        assertThat(initialCA.getSigAlgName(), is(CertificateHelper.ECDSA_SIGNING_ALGORITHM));
        verify(client).putCertificateAuthorities(putCARequestArgumentCaptor.capture());
        PutCertificateAuthoritiesRequest request = putCARequestArgumentCaptor.getValue();
        assertThat(request.coreDeviceCertificates(), is(initialCACerts));
    }

    @Test
    void GIVEN_cloud_service_error_WHEN_update_ca_type_THEN_service_in_error_state(ExtensionContext context)
            throws InterruptedException {
        ignoreExceptionOfType(context, ResourceNotFoundException.class);

        when(client.putCertificateAuthorities(any(PutCertificateAuthoritiesRequest.class))).thenThrow(
                ResourceNotFoundException.class);
        startNucleusWithConfig("config_with_ec_ca.yaml", State.ERRORED);
    }

    private X509Certificate pemToX509Certificate(String certPem) throws IOException, CertificateException {
        byte[] certBytes = certPem.getBytes(StandardCharsets.UTF_8);
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate cert;
        try (InputStream certStream = new ByteArrayInputStream(certBytes)) {
            cert = (X509Certificate) certFactory.generateCertificate(certStream);
        }
        return cert;
    }
}
