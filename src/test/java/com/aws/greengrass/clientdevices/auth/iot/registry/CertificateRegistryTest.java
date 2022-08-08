/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith({MockitoExtension.class, GGExtension.class})
class CertificateRegistryTest {
    private static final String mockCertPem = "certificatePem";
    private static final String mockCertId = "certificateId";
    @Mock
    private IotAuthClient mockIotAuthClient;
    @Captor
    private ArgumentCaptor<String> certPemCaptor;

    private CertificateRegistry registry;

    @BeforeEach
    void beforeEach() {
        registry = new CertificateRegistry(mockIotAuthClient);
    }

    @AfterEach
    void afterEach() {
        registry.clear();
    }

    @Test
    void GIVEN_validAndActiveCertificatePem_WHEN_getIotCertificateIdForPem_THEN_certificateIdReturned() {
        when(mockIotAuthClient.getActiveCertificateId(anyString())).thenReturn(Optional.of(mockCertId));

        registry.isCertificateValid(mockCertPem);
        Optional<String> certificateId = registry.getIotCertificateIdForPem(mockCertPem);

        // Assert that we only call the cloud a single time
        assertThat(certificateId.get(), is(mockCertId));
        verify(mockIotAuthClient, times(1)).getActiveCertificateId(anyString());
    }

    @Test
    void GIVEN_certificatePem_and_cloudProperResponse_WHEN_getIotCertificateIdForPem_THEN_certificateIdReturned() {
        when(mockIotAuthClient.getActiveCertificateId(anyString())).thenReturn(Optional.of(mockCertId));

        Optional<String> certificateId = registry.getIotCertificateIdForPem(mockCertPem);

        assertThat(certificateId.get(), is(mockCertId));
        verify(mockIotAuthClient).getActiveCertificateId(certPemCaptor.capture());
        assertThat(certPemCaptor.getValue(), is(mockCertPem));
    }

    @Test
    void GIVEN_cached_certificateId_WHEN_getIotCertificateIdForPem_THEN_return_cached_certificateId() {
        when(mockIotAuthClient.getActiveCertificateId(anyString())).thenReturn(Optional.of(mockCertId));

        assertThat(registry.getIotCertificateIdForPem(mockCertPem).get(), is(mockCertId));

        // request certificateId for the same certificatePem multiple times;
        // actual cloud request should be made only once and cached value should be returned for subsequent calls
        assertThat(registry.getIotCertificateIdForPem(mockCertPem).get(), is(mockCertId));
        assertThat(registry.getIotCertificateIdForPem(mockCertPem).get(), is(mockCertId));
        verify(mockIotAuthClient, times(1)).getActiveCertificateId(anyString());
    }

    @Test
    void GIVEN_inactiveCertificate_WHEN_getIotCertificateIdForPem_THEN_should_not_cache_certificateId() {
        when(mockIotAuthClient.getActiveCertificateId(anyString())).thenReturn(Optional.empty());

        assertThat(registry.getIotCertificateIdForPem(mockCertPem), is(Optional.empty()));

        // request certificateId for the same invalid certificatePem multiple times;
        // new cloud request should be made every time and result should not be cached
        assertThat(registry.getIotCertificateIdForPem(mockCertPem), is(Optional.empty()));
        assertThat(registry.getIotCertificateIdForPem(mockCertPem), is(Optional.empty()));
        verify(mockIotAuthClient, times(3)).getActiveCertificateId(anyString());
    }

    @Test
    void GIVEN_unreachable_cloud_WHEN_isCertificateValid_THEN_pass_with_cached_result() {
        // cache results before going offline
        when(mockIotAuthClient.getActiveCertificateId(anyString())).thenReturn(Optional.of(mockCertId));
        assertTrue(registry.isCertificateValid(mockCertPem));

        // go offline
        reset(mockIotAuthClient);
        doThrow(CloudServiceInteractionException.class).when(mockIotAuthClient).getActiveCertificateId(anyString());

        // verify cached result
        assertTrue(registry.isCertificateValid(mockCertPem));
        Optional<String> certificateId = registry.getIotCertificateIdForPem(mockCertPem);
        assertThat(certificateId.get(), is(mockCertId));
    }

    @Test
    void GIVEN_offline_initialization_WHEN_isCertificateValid_THEN_return_invalid_by_default() {
        doThrow(CloudServiceInteractionException.class).when(mockIotAuthClient).getActiveCertificateId(anyString());
        assertFalse(registry.isCertificateValid(mockCertPem));
        assertThat(registry.getIotCertificateIdForPem(mockCertPem), is(Optional.empty()));
    }

    @Test
    void GIVEN_certificateRegistry_with_expired_entries_WHEN_refresh_THEN_removes_stale_entries() {
        // add a stale/expired entry in the registry
        CertificateRegistry.registry.put(mockCertPem,
                new CertificateRegistryEntry(Instant.now().minusSeconds(1L), mockCertPem, mockCertId));
        assertThat(CertificateRegistry.registry.size(), is(1));

        registry.refresh();

        assertNull(CertificateRegistry.registry.get(mockCertPem));
        assertThat(CertificateRegistry.registry.size(), is(0));
    }
}
