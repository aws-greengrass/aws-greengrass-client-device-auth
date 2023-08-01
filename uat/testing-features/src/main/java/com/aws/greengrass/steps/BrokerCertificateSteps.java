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
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
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
    private static final int GET_PRINCIPALS_TIMEOUT_SEC = 5;

    private final MqttBrokers mqttBrokers;

    private final Map<String, CertificateInfo> certificateInfos = new HashMap<>();

    @AllArgsConstructor
    @Getter
    private static class CertificateInfo {
        X509Certificate certificate;                    // TODO: can be list/array
        Principal[] princinals;
    }

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
        CertificateInfo certInfoA = certificateInfos.get(certNameA);
        if (certInfoA == null) {
            throw new IllegalStateException(String.format("Certificate %s not found.", certNameA));
        }

        CertificateInfo certInfoB = certificateInfos.get(certNameB);
        if (certInfoB == null) {
            throw new IllegalStateException(String.format("Certificate %s not found.", certNameB));
        }

        if (!certInfoA.getCertificate().equals(certInfoB.getCertificate())) {
            throw new IllegalStateException("Certificates are differ");
        }
    }

    /**
     * Checks is certificate contains endpoint in ubject alternative names extension.
     *
     * @param certName the name of certificate
     * @param endpoint the name of endpoint or IP address
     * @throws CertificateParsingException when could not extract subject alternative names from certificate
     * @throws IllegalStateException on errors
     */
    @Then("I verify that the subject alternative names of certificate {string} contains endpoint {string}")
    public void verifyBrokerCertificateContainsEndpoint(String certName, String endpoint)
            throws CertificateParsingException {
        CertificateInfo certInfo = certificateInfos.get(certName);
        if (certInfo == null) {
            throw new IllegalStateException(String.format("Certificate %s not found.", certName));
        }

        Collection<List<?>> altNames = certInfo.getCertificate().getSubjectAlternativeNames();
        if (altNames == null) {
            throw new IllegalStateException("Missing Subject alternatiuve names of certificate");
        }

        for (List<?> alt : altNames) {
            Object altName = alt.get(1);
            log.info("Cert alt name {}", altName);
            if (endpoint.equals(altName)) {
                return;
            }
        }

        throw new IllegalStateException("Endpoint not found in the subject alternative names of certificate");
    }

    /**
     * Checks is certificate's accepted issuer list is missing or empty.
     *
     * @param certName the name of certificate
     * @throws IllegalStateException on errors
     */
    @Then("I verify the TLS accepted issuer list of certificate {string} is empty")
    public void verifyAcceptedIssuerListEmpty(String certName) {
        CertificateInfo certInfo = certificateInfos.get(certName);
        if (certInfo == null) {
            throw new IllegalStateException(String.format("Certificate %s not found.", certName));
        }

        Principal[] clientCertIssuers = certInfo.getPrincinals();
        if (clientCertIssuers != null && clientCertIssuers.length > 0) {
            throw new IllegalStateException("Accepted issuer list is not empty");
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
    @When("I retrieve the certificate of broker {string} and store as {string}")
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

                Principal[] principal = futures.getPrincinal().get(GET_PRINCIPALS_TIMEOUT_SEC,
                                                                            TimeUnit.SECONDS);

                // FIXME: strictly speaking we can have a array of certificates here
                X509Certificate cert = serverCerts[0];
                certificateInfos.put(certName, new CertificateInfo(cert, principal));
                log.info("Saved broker's '{}' certificate info '{}'", brokerId, cert);
                return;
            } catch (IllegalStateException | ExecutionException | TimeoutException ex) {
                lastException = ex;
            }
        }
        // probably not possible but without cause PMD warning
        if (lastException == null) {
            throw new IllegalStateException("No addresses to get certificate");
        } else if (lastException instanceof IllegalStateException) {
            throw (IllegalStateException) lastException;
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
                        log.debug("chooseClientAlias {} {}", keyType, Arrays.toString(issuers));
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
                        log.debug("checkServerTrusted {} {}", authType, Arrays.toString(chain));
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
                    log.debug("Expected IOE when connecting to {}", uri, e);
                } else {
                    serverCertsFut.completeExceptionally(e);
                    clientCertIssuersFut.completeExceptionally(e);
                }
            }
            return new CertificateFutures(serverCertsFut, clientCertIssuersFut);
        }
    }
}
