/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate;

import com.aws.greengrass.clientdevices.auth.certificate.infra.ClientCertificateStore;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class ClientCertificateStoreTest {
    @TempDir
    Path workDir;
    private ClientCertificateStore certificateStore;

    @BeforeEach
    void beforeEach() {
        certificateStore = new ClientCertificateStore(workDir);
    }

    @Test
    void GIVEN_emptyCertificateStore_WHEN_createCert_THEN_certIsStoredInWorkDir() throws IOException {
        String certPem = "pem";
        String certId = "id";

        assertThat(certificateStore.exists(certId), is(false));
        certificateStore.storePem(certId, certPem);
        assertThat(certificateStore.exists(certId), is(true));

        Optional<String> returnedPem = certificateStore.getPem(certId);
        assertThat(returnedPem.isPresent(), is(true));
        assertThat(returnedPem.get(), is(certPem));

        // Count files in work dir
        try (Stream<Path> stream = Files.walk(workDir)) {
            long count = stream.filter(Files::isRegularFile).count();
            assertThat(count, is(1L));
        }
    }

    @Test
    void GIVEN_multipleClientCertificates_WHEN_getPem_THEN_correctCertificateReturned() throws IOException {
        Function<Integer, String> getId = (i) -> "id-" + i;
        Function<Integer, String> getPem = (i) -> "pem-" + i;
        long certCount = 10;

        for (int i = 0; i < certCount; i++) {
            assertThat(certificateStore.exists(getId.apply(i)), is(false));
            certificateStore.storePem(getId.apply(i), getPem.apply(i));
        }

        for (int i = 0; i < certCount; i++) {
            String id = getId.apply(i);

            assertThat(certificateStore.exists(id), is(true));
            Optional<String> returnedPem = certificateStore.getPem(id);
            assertThat(returnedPem.isPresent(), is(true));
            assertThat(returnedPem.get(), is(getPem.apply(i)));
        }

        // Count files in work dir
        try (Stream<Path> stream = Files.walk(workDir)) {
            long count = stream.filter(Files::isRegularFile).count();
            assertThat(count, is(certCount));
        }
    }

    @Test
    void GIVEN_workDirectoryWithClientCertificates_WHEN_getPem_THEN_correctCertificateReturned() throws IOException {
        Function<Integer, String> getId = (i) -> "id-" + i;
        Function<Integer, String> getPem = (i) -> "pem-" + i;
        long certCount = 10;

        // GIVEN certificates stored in work directory
        for (int i = 0; i < certCount; i++) {
            assertThat(certificateStore.exists(getId.apply(i)), is(false));
            certificateStore.storePem(getId.apply(i), getPem.apply(i));
        }

        // Simulate restart by re-creating certificate store from work dir
        certificateStore = new ClientCertificateStore(workDir);

        for (int i = 0; i < certCount; i++) {
            String id = getId.apply(i);

            assertThat(certificateStore.exists(id), is(true));
            Optional<String> returnedPem = certificateStore.getPem(id);
            assertThat(returnedPem.isPresent(), is(true));
            assertThat(returnedPem.get(), is(getPem.apply(i)));
        }

        // Count files in work dir
        try (Stream<Path> stream = Files.walk(workDir)) {
            long count = stream.filter(Files::isRegularFile).count();
            assertThat(count, is(certCount));
        }
    }
}
