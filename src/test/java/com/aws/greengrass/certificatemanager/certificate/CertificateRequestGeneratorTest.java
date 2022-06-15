/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.InetAddress;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
@ExtendWith(GGExtension.class)
class CertificateRequestGeneratorTest {

    // TODO: Replace with Key Manager when ready
    private static final String PRIVATE_KEY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCxCHT3zdDxDTIH\n" +
            "qGpmAxFiM5mPaWf5lWawBUwTvZ+E0cWDfHZWOQkzzuC1u05ajiGcD03idM/S2olI\n" +
            "QkuKQMqBiNfkNbkNCbsfxrnQcn5p2xj5cV1TcNsGemW3lRKGkW7RCWGNqllaH0cm\n" +
            "UVIheiPfsNMo9pAyQ2WnzhkWzcY6mWd349aWn0kWxbf3EqowTc+qnjxpSLkajoZy\n" +
            "ndAmotlyl3FLJ/2KNbfEKs4Kz2EQ95SzA6JNEuPvBQ3JQGX2N6+vTkqTxiN0NUbK\n" +
            "anEJRNt3Lf5Hwh+gAy4s9CakVOvdV9r9+aR4q/4rbY0tV3k7eI5M/L7IylZobqlV\n" +
            "MiSjDEQxAgMBAAECggEACcPcp9qbfuBqdQ+uJsheihssikOXL6Y1DtqL8r7P8MSd\n" +
            "b35LDMCCqG/t5zhYvxf99JzHgAlPeWMClZDKl+qxAOoqdnUcKwLxZnRQxZW7MV2b\n" +
            "iA7fxf2Ap1/TAohfiDq3cSJiVi0iVp1JEG0F6cDR/8HCNA09sPgUwGfW7HIXUB4U\n" +
            "jywGPBaePy5UCR6hkuzG+R7ewttDKgHZKl+zs+5gNFX4xGKdN1C2/hrByNE51ha2\n" +
            "l7rOV9Y41oHQlIjNb6KzVLwgl2ZGQqhZNPh8PCHEaDJozGyB44mjg/toJtjsVcHV\n" +
            "8fkBKDIwz40tXGr8ImLH/8wanU62yzhSuQeUHZhIHQKBgQDmPO3mj/yhdockVZVr\n" +
            "6bnZp3Nhn2de5ADsWZ/rg0va/bepdeVgV1857/aC+QZEw/4KwpkYzyjWCq/01Go3\n" +
            "YrHwzMtYvELqPPqBesqjn0C1NqrpEkI7uwHcdcC8XxmPgPmQ18AKBvqY4KDYnxLv\n" +
            "NshVL3oMKX5E8xr7cgOKw/6vDwKBgQDE135fDrgEeuI3m+sZxBwlv+c703BUGpR1\n" +
            "eFHOAL2YyP26fo3rdI9e0wSkU+1Sc+J5sN2txmPPYWsLVcG324heQsnzOIpCJLQW\n" +
            "ldMhJVMSNq5+T9JMRjK+unbAb4D1xWBdSIDn5xTSJ5rbZD/Tf8XLc3lSJEQzUCPh\n" +
            "6Yi9Vn3YvwKBgQDdhohcS/QGIvY6toHzd8oTKrOwnWAng/eDcWSWhRjqVy5sxXvy\n" +
            "q357T3B/aj1m0rFPBxepMEGZWGUei3a7XaHaECIjedhkalGZXV01+5eWBEiDwsap\n" +
            "k/Bhf4b3eAUu9yio/X3P6BEcIR2B7+SW973fgLPMmevdSY+PmY7g6F3XbwKBgH3O\n" +
            "+X8J6kU6wSEnr2C9ffwGpmUPY8jtYBugdjCDOqrPM5Qh1NU7n3LMzje1SIadeI+s\n" +
            "olKx7RMbwY3yFqlLT+JnL6obZgqWaN/SjKmvsGOAvZAPtmp3p3FNvh6KZdMvfsLA\n" +
            "V1tgz2buV0SH0197V9EMEeuVMF3Xh8eZOoNBJ5gDAoGAN5ueGCnUBvXSmsv2sxeR\n" +
            "nDMyWeNiRcZ9qveFlJQ1EWN8mrQHR2qYr5+oD8HZSn/fJ3iaihMgfT4KmvJoLWW/\n" +
            "BUDnRoGEa6bhFeWylIuCmTyJHfx45yXVuZsWiBwO0LO0fgD3fHCgTgUlX87QQQIG\n" +
            "KFxYNNRhqxDWdh37IP2zmLE=";

    private static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsQh0983Q8Q0yB6hqZgMR\n" +
            "YjOZj2ln+ZVmsAVME72fhNHFg3x2VjkJM87gtbtOWo4hnA9N4nTP0tqJSEJLikDK\n" +
            "gYjX5DW5DQm7H8a50HJ+adsY+XFdU3DbBnplt5UShpFu0QlhjapZWh9HJlFSIXoj\n" +
            "37DTKPaQMkNlp84ZFs3GOplnd+PWlp9JFsW39xKqME3Pqp48aUi5Go6Gcp3QJqLZ\n" +
            "cpdxSyf9ijW3xCrOCs9hEPeUswOiTRLj7wUNyUBl9jevr05Kk8YjdDVGympxCUTb\n" +
            "dy3+R8IfoAMuLPQmpFTr3Vfa/fmkeKv+K22NLVd5O3iOTPy+yMpWaG6pVTIkowxE\n" +
            "MQIDAQAB";

    private static final String TEST_CSR = "-----BEGIN CERTIFICATE REQUEST-----\n" +
            "MIIDAjCCAeoCAQAwgYcxHDAaBgNVBAsTE0FtYXpvbiBXZWIgU2VydmljZXMxGDAW\n" +
            "BgNVBAoTD0FtYXpvbi5jb20gSW5jLjEQMA4GA1UEBxMHU2VhdHRsZTETMBEGA1UE\n" +
            "CBMKV2FzaGluZ3RvbjELMAkGA1UEBhMCVVMxGTAXBgNVBAMTEENTUkdlbmVyYXRv\n" +
            "clRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCxCHT3zdDxDTIH\n" +
            "qGpmAxFiM5mPaWf5lWawBUwTvZ+E0cWDfHZWOQkzzuC1u05ajiGcD03idM/S2olI\n" +
            "QkuKQMqBiNfkNbkNCbsfxrnQcn5p2xj5cV1TcNsGemW3lRKGkW7RCWGNqllaH0cm\n" +
            "UVIheiPfsNMo9pAyQ2WnzhkWzcY6mWd349aWn0kWxbf3EqowTc+qnjxpSLkajoZy\n" +
            "ndAmotlyl3FLJ/2KNbfEKs4Kz2EQ95SzA6JNEuPvBQ3JQGX2N6+vTkqTxiN0NUbK\n" +
            "anEJRNt3Lf5Hwh+gAy4s9CakVOvdV9r9+aR4q/4rbY0tV3k7eI5M/L7IylZobqlV\n" +
            "MiSjDEQxAgMBAAGgNTAzBgkqhkiG9w0BCQ4xJjAkMCIGA1UdEQQbMBmHBMCoAQGC\n" +
            "EXNvbWVmYWtlLmhvc3QuY29tMA0GCSqGSIb3DQEBCwUAA4IBAQBJZFtYJriJoh3c\n" +
            "+oaAFsuoCgD48R1MXqC8ua0X9jSfa/HFu6453QHvZwFyNGJXmuGN3OtlRsUSx8QX\n" +
            "dQBKzVuMxaQmT9gvOR1co6p4gUYx6xSyJ8kFVOuR7SlQ02VYR8ocSgjYNXKA3hYJ\n" +
            "LbyPCBwGgyr1jt+xa98dXGC0jIuvMN8nDLGRUkMNnpPOM9S92bjpSBFGl6/+X5uo\n" +
            "TkJHyuNqwXJzcDWAADhANSJx4d3OEyGs+JZOxjN+HcY+m73jHxugcqfzRX4oQ9ew\n" +
            "84NxfASp1jLWU+0viwQUo4eH32U22WDZrgIbYsUWp5KHbwqpwTniE2OI1E5kp54i\n" +
            "8lGRZDBC\n" +
            "-----END CERTIFICATE REQUEST-----\n";

    @Test
    void GIVEN_certificate_request_generator_WHEN_create_csr_called_THEN_return_valid_csr() throws Exception {
        // thingName that is used as Cert Common Name
        String thingName = "CSRGeneratorTest";

        // List of IPs and DNS names to be added as SAN extensions to CSR
        List<InetAddress> ipAddresses = new ArrayList<>();
        List<String> dnsNames = new ArrayList<>();
        String dnsName = "somefake.host.com";
        String ipString = "192.168.1.1";

        ipAddresses.add(InetAddress.getByName(ipString));
        dnsNames.add(dnsName);

        // Public/Private KeyPair
        // TODO: Replace with Key Manager when ready
        KeyFactory kf = KeyFactory.getInstance("RSA");
        byte[] encodedPublicKey = Base64.getDecoder().decode(PUBLIC_KEY.replace("\n", "").getBytes());
        byte[] encodedPrivateKey = Base64.getDecoder().decode(PRIVATE_KEY.replace("\n", "").getBytes());
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(encodedPublicKey));
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(encodedPrivateKey));
        KeyPair keyPair = new KeyPair(publicKey, privateKey);
        // Assert Cert Request generation
        String expectedCSR = TEST_CSR;
        // Windows support
        expectedCSR = expectedCSR.replace("\n", System.lineSeparator());
        String actualCSR = CertificateRequestGenerator.createCSR(keyPair, thingName, ipAddresses, dnsNames);
        assertThat(actualCSR, is(expectedCSR));
    }
}
