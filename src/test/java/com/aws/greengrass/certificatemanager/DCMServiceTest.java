/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager;

import com.aws.greengrass.config.Topic;
import com.aws.greengrass.certificatemanager.certificate.CertificateDownloader;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.lifecyclemanager.GreengrassService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.lifecyclemanager.exceptions.ServiceLoadException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.GGServiceTestUtil;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class DCMServiceTest extends GGServiceTestUtil {
    private static final long TEST_TIME_OUT_SEC = 30L;

    @TempDir
    Path rootDir;

    @Mock
    CertificateDownloader mockCertificateDownloader;

    private Kernel kernel;

    @BeforeEach
    void setup() {
        kernel = new Kernel();
        kernel.getContext().put(CertificateDownloader.class, mockCertificateDownloader);
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource("config.yaml").toString());
    }

    @AfterEach
    void cleanup() {
        kernel.shutdown();
    }

    void assertCaCertTopicContains(List<String> expectedCerts) {
        Topic caCertTopic = kernel.findServiceTopic(DCMService.DCM_SERVICE_NAME)
                .lookup("runtime", "certificates", "authorities");
        List<String> caPemList = (List<String>) caCertTopic.toPOJO();
        Assertions.assertNotNull(caPemList);
        assertThat(caPemList, IsIterableContainingInAnyOrder.containsInAnyOrder(expectedCerts.toArray()));
    }

    void assertDeviceCertTopicContains(Map<String, String> expectedCerts) {
        Topic deviceCertTopic = kernel.findServiceTopic(DCMService.DCM_SERVICE_NAME)
                .lookup("runtime", "certificates", "devices");
        Map<String, String> deviceCertMap = (Map<String, String>) deviceCertTopic.toPOJO();
        Assertions.assertNotNull(deviceCertMap);
        assertThat(deviceCertMap, is(expectedCerts));
    }

    @Test
    void GIVEN_GG_with_dcm_WHEN_start_kernel_THEN_dcm_starts_successfully() throws InterruptedException {
        CountDownLatch serviceRunning = new CountDownLatch(1);
        kernel.getContext().addGlobalStateChangeListener((GreengrassService service, State was, State newState) -> {
            if (service.getName().equals(DCMService.DCM_SERVICE_NAME) && service.getState()
                    .equals(State.RUNNING)) {
                serviceRunning.countDown();
            }
        });
        kernel.launch();
        Assertions.assertTrue(serviceRunning.await(TEST_TIME_OUT_SEC, TimeUnit.SECONDS));
    }

    @Test
    void GIVEN_GG_with_dcm_WHEN_subscribing_to_ca_updates_THEN_get_list_of_certs() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        kernel.getContext().addGlobalStateChangeListener((GreengrassService service, State was, State newState) -> {
            if (service.getName().equals(DCMService.DCM_SERVICE_NAME) && service.getState()
                    .equals(State.RUNNING)) {
                countDownLatch.countDown();
            }
        });
        kernel.launch();

        kernel.findServiceTopic(DCMService.DCM_SERVICE_NAME)
                .lookup("runtime", "certificates", "authorities")
                .subscribe((why, newv) -> {
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
            throws InterruptedException, ServiceLoadException {
        CountDownLatch startLatch = new CountDownLatch(1);
        kernel.getContext().addGlobalStateChangeListener((GreengrassService service, State was, State newState) -> {
            if (service.getName().equals(DCMService.DCM_SERVICE_NAME) && service.getState()
                    .equals(State.RUNNING)) {
                startLatch.countDown();
            }
        });
        kernel.launch();
        Assertions.assertTrue(startLatch.await(TEST_TIME_OUT_SEC, TimeUnit.SECONDS));

        DCMService dcmService = (DCMService) kernel.locate(DCMService.DCM_SERVICE_NAME);

        List<String> expectedCACerts = new ArrayList<>(Arrays.asList("CA1"));
        dcmService.updateCACertificateConfig(expectedCACerts);
        assertCaCertTopicContains(expectedCACerts);

        expectedCACerts.add("CA2");
        dcmService.updateCACertificateConfig(expectedCACerts);
        assertCaCertTopicContains(expectedCACerts);

        expectedCACerts.remove("CA1");
        expectedCACerts.add("CA3");
        dcmService.updateCACertificateConfig(expectedCACerts);
        assertCaCertTopicContains(expectedCACerts);
    }

    @Test
    void GIVEN_added_device_cert_WHEN_updateCertConfig_THEN_cert_topic_updated()
            throws InterruptedException, ServiceLoadException {
        CountDownLatch startLatch = new CountDownLatch(1);
        kernel.getContext().addGlobalStateChangeListener((GreengrassService service, State was, State newState) -> {
            if (service.getName().equals(DCMService.DCM_SERVICE_NAME) && service.getState()
                    .equals(State.RUNNING)) {
                startLatch.countDown();
            }
        });
        kernel.launch();
        Assertions.assertTrue(startLatch.await(TEST_TIME_OUT_SEC, TimeUnit.SECONDS));

        DCMService dcmService = (DCMService) kernel.locate(DCMService.DCM_SERVICE_NAME);

        Map<String, String> expectedDeviceCerts = new HashMap<>();
        expectedDeviceCerts.put("device1", "cert1");
        dcmService.updateDeviceCertificateConfig(expectedDeviceCerts);
        assertDeviceCertTopicContains(expectedDeviceCerts);

        expectedDeviceCerts.put("device2", "cert2");
        dcmService.updateDeviceCertificateConfig(expectedDeviceCerts);
        assertDeviceCertTopicContains(expectedDeviceCerts);

        expectedDeviceCerts.remove("device1");
        expectedDeviceCerts.put("device3", "cert3");
        dcmService.updateDeviceCertificateConfig(expectedDeviceCerts);
        assertDeviceCertTopicContains(expectedDeviceCerts);
    }
}
