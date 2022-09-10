/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


public class DomainEvents {
    private final Map<Class, CopyOnWriteArrayList<DomainEventListener>> eventMap = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface DomainEventListener<T> {
        void handle(T event);
    }

    /**
     * Register event listener.
     * @param listener Event listener callback
     * @param clazz    Type of domain event
     * @param <T>      Type of domain event
     */
    public <T> void registerListener(DomainEventListener<T> listener, Class<T> clazz) {
        CopyOnWriteArrayList<DomainEventListener> listenerList =
                eventMap.computeIfAbsent(clazz, (k) -> new CopyOnWriteArrayList<>());
        listenerList.addIfAbsent(listener);
    }

    /**
     * Emit an event.
     * @param domainEvent Domain event
     * @param <T>         Type of domain event
     */
    public <T> void emit(T domainEvent) {
        List<DomainEventListener> listenerList = eventMap.get(domainEvent.getClass());
        if (listenerList != null) {
            for (DomainEventListener<T> listener : listenerList) {
                listener.handle(domainEvent);
            }
        }
    }
}
