/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.steps;

import com.aws.greengrass.utils.MqttBrokers;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.utils.CollectionUtils;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

@Log4j2
@ScenarioScoped
public class BrokerCertificateSteps {
    private static final int GET_CERTIFICATE_TIMEOUT_SEC = 5;

    private final MqttBrokers mqttBrokers;

    private final Map<String, X509Certificate> certificates = new HashMap<>();

    @AllArgsConstructor
    @Getter
    private static class CertificateFutures {
        CompletableFuture<X509Certificate[]> certificate;
        CompletableFuture<Principal[]> princinal;
    }


    /**
     * Creates instance of BrokerCertificateSteps.
     *
     * @param mqttBrokers the connectivity info of brokers
     */
    @Inject
    public BrokerCertificateSteps(MqttBrokers mqttBrokers) {
        this.mqttBrokers = mqttBrokers;
    }

    /**
     * Checks is one certificate equals to other.
     *
     * @param certNameA the name of first certificate
     * @param certNameB the name of second certificate
     * @throws IllegalStateException on errors
     */
    @Then("I verify the certificate {string} equals the certificate {string}")
    public void verifyCertsAreEqual(String certNameA, String certNameB) {
        X509Certificate certA = certificates.get(certNameA);
        if (certA == null) {
            throw new IllegalStateException(String.format("Certificate %s not found.", certNameA));
        }

        X509Certificate certB = certificates.get(certNameB);
        if (certB == null) {
            throw new IllegalStateException(String.format("Certificate %s not found.", certNameB));
        }

        if (!certA.equals(certB)) {
            throw new IllegalStateException("Certificates are differ");
        }
    }

    /**
     * Retrive broker's certificate and store it internally for future references.
     *
     * @param brokerId the id of broker, before must be discovered or added
     * @param certName the name (alias) of certificate for future references
     * @throws InterruptedException when thread has been interrupted
     * @throws ExecutionException on future's excution exception
     * @throws TimeoutException when timed out
     * @throws IllegalStateException on errors
     */
    @When("I retrieve the certificate of broker {} and store as {}")
    public void retrieveServerCertificate(String brokerId, String certName)
                throws ExecutionException, TimeoutException, InterruptedException {

        // get address information about broker
        final List<MqttBrokers.ConnectivityInfo> bc = mqttBrokers.getConnectivityInfo(brokerId);
        if (CollectionUtils.isNullOrEmpty(bc)) {
            throw new IllegalStateException("There is no address information about broker, "
                                        + "probably discovery step missing in scenario");
        }

        Exception lastException = null;
        for (MqttBrokers.ConnectivityInfo broker : bc) {
            String host = broker.getHost();
            Integer port = broker.getPort();
            URI serverUri = URI.create(String.format("https://%s:%d", host, port));

            try {
                CertificateFutures futures = getServerCerts(serverUri);
                X509Certificate[] serverCerts = futures.getCertificate().get(GET_CERTIFICATE_TIMEOUT_SEC,
                                                                            TimeUnit.SECONDS);
                if (serverCerts == null) {
                    throw new IllegalStateException("Certificate array is null");
                }

                if (serverCerts.length <= 0) {
                    throw new IllegalStateException("Certificate array is empty");
                }

                X509Certificate cert = serverCerts[0];
                certificates.put(certName, cert);
                log.info("Saved broker's {} certificate {}", brokerId, cert);
                return;
            } catch (IllegalStateException | ExecutionException | TimeoutException ex) {
                lastException = ex;
            }
        }
        if (lastException == null) {
            throw new IllegalStateException("No addresses to get certificate");
        } else if (lastException instanceof RuntimeException) {
            throw (RuntimeException) lastException;
        } else if (lastException instanceof ExecutionException) {
            throw (ExecutionException) lastException;
        } else if (lastException instanceof TimeoutException) {
            throw (TimeoutException) lastException;
        }
    }

    /**
     * Connect to a server specified by URI and returns the server's certificates as well as
     * the client certificate issuer principals (if any) (when using mTLS). If the server
     * does not use TLS or mTLS, then either or both of the completable futures may not complete.
     *
     * @param uri URI of the server to connect to and get the certs.
     * @return completable futures for the server cert chain and the client cert issuers.
     */
    private CertificateFutures getServerCerts(URI uri) {
        return getServerCerts(uri, true);
    }

    private CertificateFutures getServerCerts(URI uri, boolean ignoreExceptions) {
        CompletableFuture<X509Certificate[]> serverCertsFut = new CompletableFuture<>();
        CompletableFuture<Principal[]> clientCertIssuersFut = new CompletableFuture<>();

        try (SdkHttpClient client = ApacheHttpClient.builder()
                .tlsKeyManagersProvider(() -> new KeyManager[]{new X509KeyManager() {
                    @Override
                    public String[] getClientAliases(String keyType, Principal[] issuers) {
                        return new String[0];
                    }

                    // This is the only API call that matters
                    @Override
                    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                        log.info("chooseClientAlias {} {}", keyType, Arrays.toString(issuers));
                        clientCertIssuersFut.complete(issuers);
                        return null;
                    }

                    @Override
                    public String[] getServerAliases(String keyType, Principal[] issuers) {
                        return new String[0];
                    }

                    @Override
                    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                        return null;
                    }

                    @Override
                    public X509Certificate[] getCertificateChain(String alias) {
                        return new X509Certificate[0];
                    }

                    @Override
                    public PrivateKey getPrivateKey(String alias) {
                        return null;
                    }
                } })
                .tlsTrustManagersProvider(() -> new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        log.info("checkServerTrusted {} {}", authType, Arrays.toString(chain));
                        serverCertsFut.complete(chain);
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                } }).build()) {
            try {
                client.prepareRequest(HttpExecuteRequest.builder()
                        .request(SdkHttpRequest.builder().method(SdkHttpMethod.GET).uri(uri).build()).build()).call();
            } catch (IOException e) {
                if (ignoreExceptions) {
                    // We expect an IOE because we're using a HTTP client to connect to MQTT, and we aren't
                    // providing a client cert either.
                    log.info("Expected IOE when connecting to {}", uri, e);
                } else {
                    serverCertsFut.completeExceptionally(e);
                    clientCertIssuersFut.completeExceptionally(e);
                }
            }
            return new CertificateFutures(serverCertsFut, clientCertIssuersFut);
        }
    }
}
