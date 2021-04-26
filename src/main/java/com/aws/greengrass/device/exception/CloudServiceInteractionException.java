package com.aws.greengrass.device.exception;

public class CloudServiceInteractionException extends RuntimeException {

    static final long serialVersionUID = -1L;

    public CloudServiceInteractionException(String message) {
        super(message);
    }

    public CloudServiceInteractionException(Throwable e) {
        super(e);
    }

    public CloudServiceInteractionException(String message, Throwable e) {
        super(message, e);
    }
}
