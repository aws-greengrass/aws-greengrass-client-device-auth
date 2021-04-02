/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager;

import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.certificatemanager.certificate.CertificateDownloader;
import com.aws.greengrass.certificatemanager.certificate.CsrProcessingException;
import com.aws.greengrass.certificatemanager.model.DeviceConfig;
import org.bouncycastle.util.io.pem.PemReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@ExtendWith({MockitoExtension.class})
public class CertificateManagerTest {
    private static final String RSA_KEY = "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC9PUlisa3uJNA0\n" +
            "cXJzOGIJimZObqTpUkKo4F3Uo9gTo8bRIqcwIDn+LAuuFZ5KKVasP4taJ+RDreHX\n" +
            "eRAIB9724e7m6Mrf/NTOiekqwbDHCM6utfSEdUvjz6xvON50OJDyZOG9V7iH/L5Y\n" +
            "/okkuhcSx5yQSZl9dTBt799osnH+vjMfAxNOLv/mJgAzriDnVs75XID4bTB+HAFX\n" +
            "937mXWGrkNtY2vNia5tnzPGvyNpO9YRkzBMxNgurTH487qvLVRX5R0AG7x+G4gxy\n" +
            "1P9RK+v6Ao2cfW3glgo5YCjsh2LHizmCn8JkQ5qoZFsMUNK+D16cvfBSGN9ZJRQY\n" +
            "62FMP4Y9AgMBAAECggEAWhooKtm2msDkcVPizoF2DFzdQVLlKkKEgqvxgSFR7Gf6\n" +
            "bfE18XAmrKKEHSsl5uGv5uuKii6cPK057sIxo+E7hAknGsKUdfmHyZ9YaYc3iEzl\n" +
            "O8Je3gKLU7i5vWsyX9vfB8qIwQJXVkg8yVrIKbiA6+wm23xvnJCDspSXpt3v8P4E\n" +
            "0/craj1rKGOBlpgalYCukrE+bdOX1aRlIwFSKjymBJxiXDcAl0CjrceXvHhisT4+\n" +
            "UscgmhnLqhccj+zgd/6lf/UMlnopLBra1IuWdOJe2A5nD29zou67Pk9d0LOS7U+O\n" +
            "UVxYhdih+ldn3SBnk09Pm4KwwpwDU6K6HQcemBrccQKBgQDkV8I7ph6TIMBQezc3\n" +
            "Exo11agnuTLmVoNP3QZ9Ey4bWX0MaBH4LDQN9QR71IBs+i1NaM2ZWHb+Pzxtp6jk\n" +
            "dB88bJNNwNXYN+xWHU2UpmorcUgxoW49sct4caVtbSFH7wL0SMjUHFkspRQbpAy3\n" +
            "2eTl4pmAtyvok75SgoVS5RJ/3wKBgQDUKQvALmhDPl57wU+fY3P90zUktzhnz3uY\n" +
            "YXSwehkfIKtL/6PC0JuNdhp9G8QV9ffVtVyIV2XHEJ0ezOrPAzU2ys2jF4rsnk82\n" +
            "BvrOlrtrt4OHzuaaIIeS0l+RVAEZbW6tPwCww+LiKIQL6gT+V+sL6vypDk0qXHiH\n" +
            "Pfs+Bp5NYwKBgBs36ss5bgOTr9joFxjVihZItZammM6kGVr3ucJG+wP/ksxby2iN\n" +
            "vFa9kh9AoV5cI/JKP9e7l9OOriFUDunMfbyyJIzsI5F2eRF6xLinbZhoFHb2CzIH\n" +
            "c32T7mgwzfrJHs0HsAi4qFiOBOuWjn2L16EfdbTGINHEardjH4lWDPJ5AoGBAIUr\n" +
            "0o1qU9XJo2MI/2Kr+ZXc6mHGhChdS1Zl7eoMahJo3j0RFQjnCozsEkpvkFC8mTlS\n" +
            "4udN/LvMSOOZetZATDm9aQCzeWd3I39lOC9O3AwMIAqyK3uZaaAWXxiI4zvDvhIP\n" +
            "Fu7dtl+BUQltdn70TrynXrr7GCK78ofptvfDDcDDAoGBANiZE8NVMe+cgE2ncZzj\n" +
            "2KxmZZ6XrZXBt/lhU9wLaIGANdrSrCVh0eaNzRtEdz3JrDoOOdV4QoPk5BZ1qUTw\n" +
            "8+OAjA+KSwDOoWtodFBGd65SaDIZqvAmj+YZ8BCd3xYeG+MkfYXl3QmP8gBUYe7Y\n" +
            "m/OxuEgMDA10YOFJt9Zo81co\n" +
            "-----END PRIVATE KEY-----\n";
    private static final String RSA_CSR = "-----BEGIN CERTIFICATE REQUEST-----\n" +
            "MIICpzCCAY8CAQAwYjELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAldBMRAwDgYDVQQH\n" +
            "DAdTZWF0dGxlMQ8wDQYDVQQKDAZBbWF6b24xDDAKBgNVBAsMA0FXUzEVMBMGA1UE\n" +
            "AwwMVGVzdCBSU0EgS2V5MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA\n" +
            "vT1JYrGt7iTQNHFyczhiCYpmTm6k6VJCqOBd1KPYE6PG0SKnMCA5/iwLrhWeSilW\n" +
            "rD+LWifkQ63h13kQCAfe9uHu5ujK3/zUzonpKsGwxwjOrrX0hHVL48+sbzjedDiQ\n" +
            "8mThvVe4h/y+WP6JJLoXEseckEmZfXUwbe/faLJx/r4zHwMTTi7/5iYAM64g51bO\n" +
            "+VyA+G0wfhwBV/d+5l1hq5DbWNrzYmubZ8zxr8jaTvWEZMwTMTYLq0x+PO6ry1UV\n" +
            "+UdABu8fhuIMctT/USvr+gKNnH1t4JYKOWAo7Idix4s5gp/CZEOaqGRbDFDSvg9e\n" +
            "nL3wUhjfWSUUGOthTD+GPQIDAQABoAAwDQYJKoZIhvcNAQELBQADggEBAEqoi4HI\n" +
            "GWbbJ8FG+OwHqMctuLW5wpJmZnsumCoC0LvNFxYtXZZjhj/IVNhJ/CJjO6+oKRsB\n" +
            "CFUJPcw+QrutxVPMep4wgvC4QWL+8POR9NkgXzdQR0I8b1r29sZENUzDKVfzaYEq\n" +
            "qUgDgXmEOp91PmRNOe3VHoIY4gIKQ/rqxVRe8mVWXg8qi2+JAKzUFQITdouSuXxD\n" +
            "hDe3d23LbwAkigrV52MRjwKPGgAKL7xx10zC7D2IpHwdUMi7byXgCP/IwLgyQZLz\n" +
            "g6FM9ak2LgmWO2UZvFV0dl+EUmYFZFXETrjE3gaM3svuYRIgWCtxYbkFjTocSTW+\n" +
            "xw6vjYSXhB49pvE=\n" +
            "-----END CERTIFICATE REQUEST-----";
    private static final String IOT_CERT_RESPONSE = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDWTCCAkGgAwIBAgIUcMgL9j0BQ6HqadaNuHh/x1WRpd0wDQYJKoZIhvcNAQEL\n" +
            "BQAwTTFLMEkGA1UECwxCQW1hem9uIFdlYiBTZXJ2aWNlcyBPPUFtYXpvbi5jb20g\n" +
            "SW5jLiBMPVNlYXR0bGUgU1Q9V2FzaGluZ3RvbiBDPVVTMB4XDTIwMDYwNTAxMTY1\n" +
            "OFoXDTQ5MTIzMTIzNTk1OVowHjEcMBoGA1UEAwwTQVdTIElvVCBDZXJ0aWZpY2F0\n" +
            "ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANgiX9P2FVDYjCdSvLdY\n" +
            "H4wpP0IrSpKzfL6FdjzzPx83cZ2EsxmgifhhEOCtbmXrXn8qECd+KtCmbxHXuVnf\n" +
            "rKRp7SEBG+rebZjgyCom3wlffQsns1DZTiL3wMsxJn5CF7qZ3c/kuxNeD7CHk8XR\n" +
            "eJk0anA5Grks8TO5opT75SE4fwvuVyVvi0n54TYM0736Zve+viVs7VfX7zuuFmYr\n" +
            "UVVO07/drT+QD9l+guV57ti0xuLj00utxuL4yf4upKuNQQjWqq6JtL4W/p5l4VZB\n" +
            "ZH/qHAJC7cBLMsovJOYtRTJM9TG0gA7zO6QpN9tOt17kkx24EE0Dyvt2ydcaC/A/\n" +
            "W6ECAwEAAaNgMF4wHwYDVR0jBBgwFoAUY1ds6Gn8cB4AbMFNdkrQJNYXleMwHQYD\n" +
            "VR0OBBYEFMDPfTQjWsyNXxQczxMfhI7JymQIMAwGA1UdEwEB/wQCMAAwDgYDVR0P\n" +
            "AQH/BAQDAgeAMA0GCSqGSIb3DQEBCwUAA4IBAQBHVKnpobp93Jo1bvg5M4CG3wCq\n" +
            "f40eRErlcZ9XmuYHwzNXjOQCVE14BcuuwefWi1BcCgcjScI7Dxp8PAlL2GtOsl/l\n" +
            "va/XaWoS93bEULTNc8rcm54wnEiQiZf4IaMrljPDwJOOWXQHglpnbfTQCgRE7Mev\n" +
            "7YytSBhlUbgLCEE/IJVbD7aM9vn1t63zqJAnVXsqs5DTvf5+2qBzD0+gkkuSTbVj\n" +
            "67kQAMZs/MBVR2+94Ka5jAPSmotUwJADNQHKD5wB1vLc4vi7TfyDCA/dG0WtDmrx\n" +
            "knISpDCZWWbdcCNFfZVmHGg7F5VBVUCcrB2bz+E9W1PCBYyMCrEJywGxva+w\n" +
            "-----END CERTIFICATE-----\n";
    private static final int TEST_TIME_OUT_SEC = 1;

    @Mock
    CertificateDownloader mockCertificateDownloader;

    @TempDir
    Path tmpPath;

    private CertificateManager certificateManager;

    @BeforeEach
    public void beforeEach() throws KeyStoreException {
        certificateManager = new CertificateManager(mockCertificateDownloader, new CertificateStore(tmpPath));
        certificateManager.update("", CertificateStore.CAType.RSA_2048);
    }

    public static PrivateKey getRsaPrivateKeyFromPem(String privateKeyString)
            throws InvalidKeySpecException, NoSuchAlgorithmException, IOException {
        try (PemReader reader = new PemReader(new StringReader(privateKeyString))) {
            EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(reader.readPemObject().getContent());
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        }
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
    public void GIVEN_device_list_containing_previously_downloaded_cert_WHEN_setDeviceConfigurations_THEN_certs_are_retrieved_from_disk() {
        List<DeviceConfig> deviceConfigList = new ArrayList<>();
        DeviceConfig dc = new DeviceConfig("deviceArn", "certificateId");
        deviceConfigList.add(dc);

        Mockito.when(mockCertificateDownloader.downloadSingleDeviceCertificate(Mockito.any()))
                .thenReturn("certificatePem");
        certificateManager.setDeviceConfigurations(deviceConfigList);

        // Certificate is downloaded first time and stored to disk
        Mockito.verify(mockCertificateDownloader, Mockito.times(1))
                .downloadSingleDeviceCertificate(dc.getCertificateId());

        // Remove device from config list
        certificateManager.setDeviceConfigurations(new ArrayList<>());

        // Certificate should be read from disk
        certificateManager.setDeviceConfigurations(deviceConfigList);
        Mockito.verify(mockCertificateDownloader, Mockito.times(1))
                .downloadSingleDeviceCertificate(dc.getCertificateId());
    }

    @Test
    public void GIVEN_deviceConfig_WHEN_getDeviceConfigurations_THEN_all_device_certs_returned() {
        List<DeviceConfig> deviceConfigList = new ArrayList<>();
        DeviceConfig dc = new DeviceConfig("deviceArn", "certificateId");
        DeviceConfig dc2 = new DeviceConfig("deviceArn2", "certificateId2");
        deviceConfigList.add(dc);
        deviceConfigList.add(dc2);

        Mockito.when(mockCertificateDownloader.downloadSingleDeviceCertificate(Mockito.any()))
                .thenReturn("certificatePem");
        certificateManager.setDeviceConfigurations(deviceConfigList);

        Map<String, String> returnedDeviceCerts = certificateManager.getDeviceCertificates();
        Assertions.assertEquals(returnedDeviceCerts.size(), deviceConfigList.size());
        Assertions.assertTrue(returnedDeviceCerts.containsKey(dc.getDeviceArn()));
        Assertions.assertTrue(returnedDeviceCerts.containsKey(dc2.getDeviceArn()));
    }

    @Test
    public void GIVEN_fewer_deviceConfigs_WHEN_getDeviceConfigurations_THEN_fewer_device_certs_returned() {
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
        Map<String, String> returnedDeviceCerts = certificateManager.getDeviceCertificates();
        Assertions.assertEquals(returnedDeviceCerts.size(), deviceConfigList.size());
        Assertions.assertTrue(returnedDeviceCerts.containsKey(dc.getDeviceArn()));
    }

    @Test
    public void GIVEN_deviceCertificates_WHEN_import_into_keystore_THEN_success() {
        List<DeviceConfig> deviceConfigList = new ArrayList<>();
        DeviceConfig dc = new DeviceConfig("deviceArn", "certificateId");
        deviceConfigList.add(dc);

        Mockito.when(mockCertificateDownloader.downloadSingleDeviceCertificate(Mockito.any()))
                .thenReturn(IOT_CERT_RESPONSE);
        certificateManager.setDeviceConfigurations(deviceConfigList);

        Map<String, String> returnedDeviceCerts = certificateManager.getDeviceCertificates();
        try {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("deviceArn", pemToX509Certificate(returnedDeviceCerts.get("deviceArn")));
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void GIVEN_default_cert_manager_WHEN_getCACertificates_THEN_single_ca_returned()
            throws CertificateEncodingException, KeyStoreException, IOException {
        List<String> caPemList = certificateManager.getCACertificates();
        Assertions.assertEquals(1, caPemList.size(), "expected single CA certificate");
    }

    @Test
    public void GIVEN_valid_csr_WHEN_subscribeToCertificateUpdates_THEN_certificate_received()
            throws InterruptedException, KeyStoreException, CsrProcessingException {
        CountDownLatch certificateReceived = new CountDownLatch(1);
        Consumer<X509Certificate> cb = t -> {
            certificateReceived.countDown();
        };

        certificateManager.subscribeToServerCertificateUpdates(RSA_CSR, cb);
        Assertions.assertTrue(certificateReceived.await(TEST_TIME_OUT_SEC, TimeUnit.SECONDS));
    }

    @Test
    public void GIVEN_generatedCertificate_WHEN_importing_into_java_keystore_THEN_success()
            throws KeyStoreException, CsrProcessingException {
        Consumer<X509Certificate> cb = t -> {
            try {
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null, null);
                ks.setKeyEntry("key", getRsaPrivateKeyFromPem(RSA_KEY), "".toCharArray(), new X509Certificate[]{t});
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException
                    | CertificateException | InvalidKeySpecException e) {
                Assertions.fail(e);
            }
        };

        certificateManager.subscribeToServerCertificateUpdates(RSA_CSR, cb);
    }

    @Test
    public void GIVEN_null_parameters_WHEN_subscribeToCertificateUpdates_THEN_throws_npe() {
        Consumer<X509Certificate> cb = t -> {};
        Assertions.assertThrows(NullPointerException.class, () ->
                certificateManager.subscribeToServerCertificateUpdates(null, cb));
        Assertions.assertThrows(NullPointerException.class, () ->
                certificateManager.subscribeToServerCertificateUpdates(RSA_CSR, null));
    }

    @Test
    public void GIVEN_invalid_pem_WHEN_subscribeToCertificateUpdates_THEN_throws_CsrProcessingException() {
        Consumer<X509Certificate> cb = t -> {};
        Assertions.assertThrows(CsrProcessingException.class, () ->
                certificateManager.subscribeToServerCertificateUpdates("INVALID_PEM", cb));
    }

    @Test
    public void GIVEN_corrupt_csr_WHEN_subscribeToCertificateUpdates_THEN_throws_CsrProcessingException() {
        Consumer<X509Certificate> cb = t -> {};
        String badCsr = "-----BEGIN CERTIFICATE REQUEST-----\n" +
                "MIICXzCCAUcCAQAwGjELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAldBMIIBIjANBgkq\n" +
                "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArcwozZOwBmYtEcbfTJjtF9NTVk87B1QQ\n" +
                "efyaGgpd+kjjHlnEidzMOI6OlDLTOpxOZP7HWs3fxrr7kjJPWj2QdxBdGFhgyhz5\n" +
                "fc6tCJ1ed34MEQSsnwZemZNAUXRaKDpDdZtnzbL2fSnGyVG53TsAsNVH5Xxy29t+\n" +
                "Ti+gDqOZdfU/43+wR1VSwn3r3wiocyYw5CR/SsH8l2bGwsChXMK0o0OqIw72CdCm\n" +
                "/XU90Fi03NWpf8c1wV+4V+S0DWXnY84VdkESkeQlNvzwgHM=\n" +
                "-----END CERTIFICATE REQUEST-----";
        Assertions.assertThrows(CsrProcessingException.class, () ->
                certificateManager.subscribeToServerCertificateUpdates(badCsr, cb));
    }
}
