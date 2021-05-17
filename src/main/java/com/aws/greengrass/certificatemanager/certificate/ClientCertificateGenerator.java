package com.aws.greengrass.certificatemanager.certificate;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
     * @param connectivityInfoSupplier ConnectivityInfo Supplier (not used in this implementation)
     * @throws KeyStoreException              KeyStoreException
     * @throws CertificateGenerationException CertificateGenerationException
     */
    @Override
    public synchronized void generateCertificate(Supplier<List<ConnectivityInfo>> connectivityInfoSupplier) throws
            KeyStoreException, CertificateGenerationException {
        Instant now = Instant.now(clock);
        try {
            certificate = CertificateHelper.signClientCertificateRequest(
                    certificateStore.getCACertificate(),
                    certificateStore.getCAPrivateKey(),
                    subject,
                    publicKey,
                    Date.from(now),
                    Date.from(now.plusSeconds(DEFAULT_CERT_EXPIRY_SECONDS)));
        } catch (NoSuchAlgorithmException | OperatorCreationException | CertificateException | IOException e) {
            throw new CertificateGenerationException(e);
        }

        X509Certificate caCertificate = certificateStore.getCACertificate();
        X509Certificate[] chain = {certificate, caCertificate};
        callback.accept(chain);
    }
}
