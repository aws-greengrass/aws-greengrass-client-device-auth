package com.aws.greengrass.certificatemanager.certificate;

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

    private final Consumer<X509Certificate> callback;

    private List<ConnectivityInfo> connectivityInfoItems = Collections.emptyList();

    /**
     * Constructor.
     *
     * @param subject          X500 subject
     * @param publicKey        Public Key
     * @param callback         Callback that consumes generated certificate
     * @param certificateStore CertificateStore instance
     */
    public ServerCertificateGenerator(X500Name subject, PublicKey publicKey, Consumer<X509Certificate> callback,
                                      CertificateStore certificateStore) {
        super(subject, publicKey, certificateStore);
        this.callback = callback;
    }

    /**
     * Regenerates certificate.
     *
     * @param connectivityInfos CIS connectivity info list
     * @throws KeyStoreException         KeyStoreException
     * @throws OperatorCreationException OperatorCreationException
     * @throws CertificateException      CertificateException
     * @throws IOException               IOException
     * @throws NoSuchAlgorithmException  NoSuchAlgorithmException
     */
    @Override
    public void generateCertificate(List<ConnectivityInfo> connectivityInfos) throws
            KeyStoreException, OperatorCreationException, CertificateException, NoSuchAlgorithmException, IOException {
        connectivityInfoItems = connectivityInfos;
        generateCertificate();
    }

    /**
     * Regenerates certificate.
     *
     * @throws KeyStoreException         KeyStoreException
     * @throws OperatorCreationException OperatorCreationException
     * @throws CertificateException      CertificateException
     * @throws IOException               IOException
     * @throws NoSuchAlgorithmException  NoSuchAlgorithmException
     */
    @Override
    public synchronized void generateCertificate() throws
            KeyStoreException, OperatorCreationException, CertificateException, NoSuchAlgorithmException, IOException {
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
