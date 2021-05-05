package com.aws.greengrass.device.exception;

// unchecked exception for backward compatibility
// TODO change to checked exception once broker exception handling change pushed in
public class AuthenticationException extends RuntimeException {

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
