package com.aws.greengrass.certificatemanager.certificate;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

public class ClientCertificateGenerator extends CertificateGenerator {

    private final Consumer<X509Certificate[]> callback;

    /**
     * Constructor.
     *
     * @param subject          X500 subject
     * @param publicKey        Public Key
     * @param callback         Callback that consumes generated certificate
     * @param certificateStore CertificateStore instance
     */
    public ClientCertificateGenerator(X500Name subject, PublicKey publicKey, Consumer<X509Certificate[]> callback,
                                      CertificateStore certificateStore) {
        super(subject, publicKey, certificateStore);
        this.callback = callback;
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
    public synchronized void generateCertificate() throws KeyStoreException,
            OperatorCreationException, CertificateException, NoSuchAlgorithmException, IOException {
        Instant now = Instant.now(clock);
        certificate = CertificateHelper.signClientCertificateRequest(
                certificateStore.getCACertificate(),
                certificateStore.getCAPrivateKey(),
                subject,
                publicKey,
                Date.from(now),
                Date.from(now.plusSeconds(DEFAULT_CERT_EXPIRY_SECONDS)));

        X509Certificate caCertificate = certificateStore.getCACertificate();
        X509Certificate[] chain = {certificate, caCertificate};
        callback.accept(chain);
    }
}
