package com.aws.greengrass.certificatemanager.certificate;

public class CertificateGenerationException extends RuntimeException {
    static final long serialVersionUID = -3387516993124229948L;

    public CertificateGenerationException(Throwable cause) {
        super(cause);
    }
}
