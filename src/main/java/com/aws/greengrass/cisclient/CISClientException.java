package com.aws.greengrass.cisclient;

public class CISClientException extends Exception {
    static final long serialVersionUID = -3387516993124229948L;

    public CISClientException(Throwable e) {
        super(e);
    }
}
