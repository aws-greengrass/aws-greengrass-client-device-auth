package com.aws.greengrass.device.iot;

public final class Authorizer {
    public static final Authorizer INSTANCE = new Authorizer();

    private Authorizer() {
    }

    public static Authorizer getInstance() {
        return INSTANCE;
    }

    public boolean isAuthorized(String sessionId, String action, String resource) {
        // TODO
        return true;
    }
}
