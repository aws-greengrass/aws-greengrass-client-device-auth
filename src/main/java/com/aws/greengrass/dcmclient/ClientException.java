package com.aws.greengrass.dcmclient;

public class ClientException extends Exception {
    static final long serialVersionUID = -3387516993124229948L;

    public ClientException(Throwable e) {
        super(e);
    }
}
