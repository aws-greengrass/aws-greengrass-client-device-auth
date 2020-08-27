/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.dcm.certificate;

import com.aws.iot.evergreen.dcm.model.DeviceConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@ExtendWith({MockitoExtension.class})
public class CertificateManagerTest {
    // Generated using a 2048 bit RSA key
    private static final String RSA_CSR = "-----BEGIN CERTIFICATE REQUEST-----\n" +
            "MIICXzCCAUcCAQAwGjELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAldBMIIBIjANBgkq\n" +
            "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArcwozZOwBmYtEcbfTJjtF9NTVk87B1QQ\n" +
            "efyaGgpd+kjjHlnEidzMOI6OlDLTOpxOZP7HWs3fxrr7kjJPWj2QdxBdGFhgyhz5\n" +
            "fc6tCJ1ed34MEQSsnwZemZNAUXRaKDpDdZtnzbL2fSnGyVG53TsAsNVH5Xxy29t+\n" +
            "R95T+Hs8h3QpRDikxWt+5u+Vgt8BL0TgEgyBKjYb6i74O5/Wohp8MKw4O/MP/jfh\n" +
            "G3EYppc/+OZ1jFaqafPKgCc/fLh9WjGE+tFiRH6UiBubidL6zAOpkCii0stiGQea\n" +
            "KkP2vqFzScLian3prtmQtrsqJ4/st/AqKO2FGY4qj4GOVic9u0NJlwIDAQABoAAw\n" +
            "DQYJKoZIhvcNAQELBQADggEBAHzEyH0VkoHUWkYjpwTZyrLmR7JyYtLTEfJ784wq\n" +
            "Tco40KFWndnbxoba9by/f0cULMONyI4sD9LM3gGtsYHOSDE7L1wqZZXGc7nlEmQn\n" +
            "n1eIyme+owpcPNLXn6KnviZYZnjbqFOdEoLZFP+Ge3tY1GOdBoVvsd4p9O/Qgx7D\n" +
            "eJpyBdc6tZ3+m3A+48lGykL1aFoiIllw8rpAhcl61yaejze+mBXvj0wCiVl8Og7e\n" +
            "Ti+gDqOZdfU/43+wR1VSwn3r3wiocyYw5CR/SsH8l2bGwsChXMK0o0OqIw72CdCm\n" +
            "/XU90Fi03NWpf8c1wV+4V+S0DWXnY84VdkESkeQlNvzwgHM=\n" +
            "-----END CERTIFICATE REQUEST-----";
    private static final int TEST_TIME_OUT_SEC = 1;

    @Mock
    CertificateDownloader mockCertificateDownloader;

    private CertificateManager certificateManager;

    @BeforeEach
    public void beforeEach() throws KeyStoreException {
        certificateManager = new CertificateManager(mockCertificateDownloader);
        certificateManager.initialize();
    }

    @Test
    public void GIVEN_single_device_WHEN_setDeviceConfigurations_THEN_certificate_downloaded() {
        List<DeviceConfig> deviceConfigList = new ArrayList<>();
        DeviceConfig dc = new DeviceConfig("deviceArn", "certificateId");
        deviceConfigList.add(dc);

        Mockito.when(mockCertificateDownloader.downloadSingleDeviceCertificate(Mockito.any()))
                .thenReturn("certificatePem");
        certificateManager.setDeviceConfigurations(deviceConfigList);
        Mockito.verify(mockCertificateDownloader, Mockito.times(1))
                .downloadSingleDeviceCertificate(dc.getCertificateId());
    }

    @Test
    public void GIVEN_new_devices_WHEN_setDeviceConfigurations_THEN_only_new_certificates_are_downloaded() {
        List<DeviceConfig> deviceConfigList = new ArrayList<>();
        DeviceConfig dc = new DeviceConfig("deviceArn", "certificateId");
        deviceConfigList.add(dc);

        Mockito.when(mockCertificateDownloader.downloadSingleDeviceCertificate(Mockito.any()))
                .thenReturn("certificatePem");
        certificateManager.setDeviceConfigurations(deviceConfigList);

        DeviceConfig dc2 = new DeviceConfig("deviceArn2", "certificateId2");
        deviceConfigList.add(dc2);

        certificateManager.setDeviceConfigurations(deviceConfigList);
        Mockito.verify(mockCertificateDownloader, Mockito.times(1))
                .downloadSingleDeviceCertificate(dc.getCertificateId());
        Mockito.verify(mockCertificateDownloader, Mockito.times(1))
                .downloadSingleDeviceCertificate(dc2.getCertificateId());
    }

    @Test
    public void GIVEN_deviceConfig_WHEN_getDeviceConfigurations_THEN_all_device_configs_returned() {
        List<DeviceConfig> deviceConfigList = new ArrayList<>();
        DeviceConfig dc = new DeviceConfig("deviceArn", "certificateId");
        DeviceConfig dc2 = new DeviceConfig("deviceArn2", "certificateId2");
        deviceConfigList.add(dc);
        deviceConfigList.add(dc2);

        Mockito.when(mockCertificateDownloader.downloadSingleDeviceCertificate(Mockito.any()))
                .thenReturn("certificatePem");
        certificateManager.setDeviceConfigurations(deviceConfigList);

        List<DeviceConfig> returnedDeviceConfigs = certificateManager.getDeviceConfigurations();
        Assertions.assertEquals(returnedDeviceConfigs.size(), deviceConfigList.size());
        Assertions.assertTrue(deviceConfigList.containsAll(returnedDeviceConfigs));
    }

    @Test
    public void GIVEN_fewer_deviceConfigs_WHEN_getDeviceConfigurations_THEN_fewer_deviceConfigs_returned() {
        List<DeviceConfig> deviceConfigList = new ArrayList<>();
        DeviceConfig dc = new DeviceConfig("deviceArn", "certificateId");
        DeviceConfig dc2 = new DeviceConfig("deviceArn2", "certificateId2");
        deviceConfigList.add(dc);
        deviceConfigList.add(dc2);

        Mockito.when(mockCertificateDownloader.downloadSingleDeviceCertificate(Mockito.any()))
                .thenReturn("certificatePem");
        certificateManager.setDeviceConfigurations(deviceConfigList);

        deviceConfigList.remove(dc2);
        certificateManager.setDeviceConfigurations(deviceConfigList);
        List<DeviceConfig> returnedDeviceConfigs = certificateManager.getDeviceConfigurations();
        Assertions.assertEquals(returnedDeviceConfigs.size(), deviceConfigList.size());
        Assertions.assertTrue(deviceConfigList.containsAll(returnedDeviceConfigs));
    }

    @Test
    public void GIVEN_valid_csr_WHEN_subscribeToCertificateUpdates_THEN_certificate_received()
            throws InterruptedException, KeyStoreException, CsrProcessingException {
        CountDownLatch certificateReceived = new CountDownLatch(1);
        Consumer<String> cb = t -> {
            certificateReceived.countDown();
        };

        certificateManager.subscribeToCertificateUpdates(RSA_CSR, cb);
        Assertions.assertTrue(certificateReceived.await(TEST_TIME_OUT_SEC, TimeUnit.SECONDS));
    }

    @Test
    public void GIVEN_null_parameters_WHEN_subscribeToCertificateUpdates_THEN_throws_npe() {
        Consumer<String> cb = t -> {};
        Assertions.assertThrows(NullPointerException.class, () ->
                certificateManager.subscribeToCertificateUpdates(null, cb));
        Assertions.assertThrows(NullPointerException.class, () ->
                certificateManager.subscribeToCertificateUpdates(RSA_CSR, null));
    }

    @Test
    public void GIVEN_invalid_pem_WHEN_subscribeToCertificateUpdates_THEN_throws_CsrProcessingException() {
        Consumer<String> cb = t -> {};
        Assertions.assertThrows(CsrProcessingException.class, () ->
                certificateManager.subscribeToCertificateUpdates("INVALID_PEM", cb));
    }

    @Test
    public void GIVEN_corrupt_csr_WHEN_subscribeToCertificateUpdates_THEN_throws_CsrProcessingException() {
        Consumer<String> cb = t -> {};
        String badCsr = "-----BEGIN CERTIFICATE REQUEST-----\n" +
                "MIICXzCCAUcCAQAwGjELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAldBMIIBIjANBgkq\n" +
                "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArcwozZOwBmYtEcbfTJjtF9NTVk87B1QQ\n" +
                "efyaGgpd+kjjHlnEidzMOI6OlDLTOpxOZP7HWs3fxrr7kjJPWj2QdxBdGFhgyhz5\n" +
                "fc6tCJ1ed34MEQSsnwZemZNAUXRaKDpDdZtnzbL2fSnGyVG53TsAsNVH5Xxy29t+\n" +
                "Ti+gDqOZdfU/43+wR1VSwn3r3wiocyYw5CR/SsH8l2bGwsChXMK0o0OqIw72CdCm\n" +
                "/XU90Fi03NWpf8c1wV+4V+S0DWXnY84VdkESkeQlNvzwgHM=\n" +
                "-----END CERTIFICATE REQUEST-----";
        Assertions.assertThrows(CsrProcessingException.class, () ->
                certificateManager.subscribeToCertificateUpdates(badCsr, cb));
    }
}
