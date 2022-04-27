package com.aws.greengrass.device.exception;

public class InvalidSessionException extends Exception {
    static final long serialVersionUID = -1L;

    public InvalidSessionException(String message) {
        super(message);
    }

    public InvalidSessionException(Throwable e) {
        super(e);
    }

    public InvalidSessionException(String message, Throwable e) {
        super(message, e);
    }
}
