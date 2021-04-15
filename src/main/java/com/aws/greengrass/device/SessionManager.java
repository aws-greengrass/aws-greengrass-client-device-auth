package com.aws.greengrass.device;

import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.iot.Certificate;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Singleton class for managing AuthN and AuthZ session.
 */
public class SessionManager {

    private final ConcurrentMap<String, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * find session by id.
     *
     * @param sessionId session identifier
     * @return session or null
     */
    public Session findSession(String sessionId) {
        return sessionMap.getOrDefault(sessionId, null);
    }

    /**
     * create session with certificate.
     *
     * @param certificate certificate stored in session
     * @return session id
     */
    public String createSession(Certificate certificate) {
        String sessionId = generateSessionId();
        sessionMap.put(sessionId, new Session(certificate));
        return sessionId;
    }

    /**
     * close the session by id.
     *
     * @param sessionId session identifier
     * @throws AuthorizationException if no session associated with sessionId
     */
    public synchronized void closeSession(String sessionId) throws AuthorizationException {
        if (!sessionMap.containsKey(sessionId)) {
            throw new AuthorizationException(String.format("No session is associated with session id (%s)", sessionId));
        }
        sessionMap.remove(sessionId);
    }

    private String generateSessionId() {
        //TODO better session id format?
        return UUID.randomUUID().toString();
    }
}
