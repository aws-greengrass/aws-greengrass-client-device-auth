/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
public class CertificateHelperTest {

    @Test
    public void GIVEN_valid_rsa_csr_and_rsa_ca_WHEN_signCertificateRequest_THEN_return_valid_certificate() {
        // TODO
    }

    @Test
    public void GIVEN_valid_ec_csr_and_ec_ca_WHEN_signCertificateRequest_THEN_return_valid_certificate() {
        // TODO
    }

    @Test
    public void GIVEN_valid_rsa_csr_and_ec_ca_WHEN_signCertificateRequest_THEN_return_valid_certificate() {
        // TODO
    }

    @Test
    public void GIVEN_valid_ec_csr_and_rsa_ca_WHEN_signCertificateRequest_THEN_return_valid_certificate() {
        // TODO
    }

    @Test
    public void GIVEN_invalid_csr_WHEN_signCertificateRequest_THEN_throws() {
        // TODO
    }

    @Test
    public void GIVEN_invalid_ca_WHEN_signCertificateRequest_THEN_throws() {
        // TODO
    }

    @Test
    public void GIVEN_ca_key_does_not_match_ca_cert_WHEN_signCertificateRequest_THEN_throws() {
        // TODO
    }

    @Test
    public void GIVEN_notAfter_before_notBefore_WHEN_signCertificateRequest_THEN_throws() {
        // TODO
    }

    @Test
    public void GIVEN_csr_with_ip_address_WHEN_signCertificateRequest_THEN_returns_correct_certificate() {
        // TODO
    }

    @Test
    public void GIVEN_csr_with_dns_name_WHEN_signCertificateRequest_THEN_returns_correct_certificate() {
        // TODO
    }

    @Test
    public void GIVEN_csr_with_dns_and_ip_WHEN_signCertificateRequest_THEN_returns_correct_certificate() {
        // TODO
    }
}
