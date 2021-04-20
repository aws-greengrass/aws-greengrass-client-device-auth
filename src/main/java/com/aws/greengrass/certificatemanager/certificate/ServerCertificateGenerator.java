package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.dcmclient.Client;
import com.aws.greengrass.dcmclient.ClientException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;
import software.amazon.awssdk.services.greengrass.model.ConnectivityInfo;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

public class ServerCertificateGenerator extends CertificateGenerator {

    private final Client cisClient;
    private final Consumer<X509Certificate> callback;

    private List<ConnectivityInfo> connectivityInfoItems = Collections.emptyList();

    /**
     * Constructor.
     *
     * @param subject          X500 subject
     * @param publicKey        Public Key
     * @param callback         Callback that consumes generated certificate
     * @param certificateStore CertificateStore instance
     * @param cisClient        CIS client
     */
    public ServerCertificateGenerator(X500Name subject, PublicKey publicKey, Consumer<X509Certificate> callback,
                                      CertificateStore certificateStore, Client cisClient) {
        super(subject, publicKey, certificateStore);
        this.callback = callback;
        this.cisClient = cisClient;
    }

    /**
     * Regenerates certificate.
     *
     * @param cisChanged true if CIS connectivity info changed, else false
     * @throws KeyStoreException         KeyStoreException
     * @throws OperatorCreationException OperatorCreationException
     * @throws CertificateException      CertificateException
     * @throws IOException               IOException
     * @throws NoSuchAlgorithmException  NoSuchAlgorithmException
     */
    @Override
    public synchronized void generateCertificate(boolean cisChanged) throws KeyStoreException,
            OperatorCreationException, CertificateException, NoSuchAlgorithmException, IOException, ClientException {
        if (cisChanged) {
            connectivityInfoItems = cisClient.getConnectivityInfo();
        }

        Instant now = Instant.now(clock);
        certificate = CertificateHelper.signServerCertificateRequest(
                certificateStore.getCACertificate(),
                certificateStore.getCAPrivateKey(),
                subject,
                publicKey,
                connectivityInfoItems,
                Date.from(now),
                Date.from(now.plusSeconds(DEFAULT_CERT_EXPIRY_SECONDS)));

        callback.accept(certificate);
    }
}
