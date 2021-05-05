package com.aws.greengrass.device.exception;

public class AuthenticationException extends Exception {

    static final long serialVersionUID = -1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(Throwable e) {
        super(e);
    }

    public AuthenticationException(String message, Throwable e) {
        super(message, e);
    }
}
