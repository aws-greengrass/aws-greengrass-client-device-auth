/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.InvalidCertificateAuthorityException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.security.exceptions.ServiceUnavailableException;
import com.aws.greengrass.util.Pair;
import com.aws.greengrass.util.RetryUtils;

import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import javax.inject.Inject;

/**
 * Registers a custom certificate authority with a private key and certificate provide through the configuration.
 * The CA is used to issue certificates to other Greengrass plugins and verify device access.
 */
public class ConfigureCustomCertificateAuthority implements UseCases.UseCase<Void, CDAConfiguration> {
    private static final Logger logger = LogManager.getLogger(ConfigureCustomCertificateAuthority.class);
    private final SecurityService securityService;
    private final CertificateStore certificateStore;


    /**
     * Configure core certificate authority.
     *
     * @param securityService      Security Service
     * @param certificateStore      Helper class for managing certificate
     */
    @Inject
    public ConfigureCustomCertificateAuthority(SecurityService securityService, CertificateStore certificateStore) {
        this.securityService =  securityService;
        this.certificateStore = certificateStore;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Pair<PrivateKey, X509Certificate[]> getCertificateChain(URI privateKeyUri, URI certChainUri) {
        // TODO: Move retry logic out of useCases
        RetryUtils.RetryConfig retryConfig = RetryUtils.RetryConfig.builder()
                .initialRetryInterval(Duration.ofMillis(200)).maxAttempt(3)
                .retryableExceptions(Collections.singletonList(ServiceUnavailableException.class)).build();

        try {
            KeyPair keyPair = RetryUtils.runWithRetry(retryConfig,
                    () -> securityService.getKeyPair(privateKeyUri, certChainUri),
                    "get-key-pair", logger);

            X509Certificate[] chain = RetryUtils.runWithRetry(retryConfig,
                    () -> securityService.getCertificateChain(privateKeyUri, certChainUri),
                    "get-certificate-chain", logger);

            return new Pair<>(keyPair.getPrivate(), chain);
        } catch (Exception e) {
            throw new InvalidCertificateAuthorityException(e);
        }
    }


    @Override
    public Result apply(CDAConfiguration configuration) {
        // TODO: We should not be passing the entire configuration just what changed. We are just doing it for
        //  its convenience but eventually syncing the runtime config can be its own use case triggered by events.

        // TODO: We need to synchronize the changes that configuration has on the state of the service. There is
        //  a possibility that 2 threads run different use cases and change the certificate authority concurrently
        //  causing potential race conditions
        if (!configuration.isUsingCustomCA()) {
            String errorMsg = "Invalid configuration: certificateUri and privateKeyUri are required.";
            return Result.warning(new InvalidConfigurationException(errorMsg));
        }

        URI privateKeyUri = configuration.getPrivateKeyUri().get();
        URI certificateUri = configuration.getCertificateUri().get();

        logger.atInfo().kv("privateKeyUri", privateKeyUri).kv("certificateUri", certificateUri)
                .log("Configuring custom core CA");

        try {
            Pair<PrivateKey, X509Certificate[]> result = getCertificateChain(privateKeyUri, certificateUri);
            certificateStore.setCaPrivateKey(result.getLeft());
            certificateStore.setCaCertificateChain(result.getRight());
            configuration.updateCACertificates(
                    Collections.singletonList(CertificateHelper.toPem(certificateStore.getCaCertificateChain())));
        } catch (CertificateEncodingException | InvalidCertificateAuthorityException | KeyStoreException
                | IOException  e) {
            logger.atError().kv("privateKeyUri", privateKeyUri).kv("certificateUri",certificateUri)
                    .cause(e).log("Failed to configure CA");
            return Result.error(e);
        }

        return Result.ok();
    }
}
