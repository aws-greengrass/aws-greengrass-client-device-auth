/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@SuppressWarnings({"rawtypes","unchecked"})
public class DomainEvents {
    private final Map<Class, CopyOnWriteArrayList<DomainEventListener>> eventListeners = new ConcurrentHashMap<>();
    private final Logger logger = LogManager.getLogger(DomainEvents.class);


    @FunctionalInterface
    public interface DomainEventListener<T extends DomainEvent> {
        Result<?> handle(T event);
    }

    /**
     * Register event listener.
     * @param listener Event listener callback
     * @param clazz    Type of domain event
     * @param <T>      Type of domain event
     */
    public <T extends DomainEvent> void registerListener(DomainEventListener<T> listener, Class<T> clazz) {
        CopyOnWriteArrayList<DomainEventListener> listeners =
                eventListeners.computeIfAbsent(clazz, (k) -> new CopyOnWriteArrayList<>());
        listeners.addIfAbsent(listener);
    }

    /**
     * Emit an event.
     * @param domainEvent Domain event
     * @param <T>         Type of domain event
     */
    public <T extends DomainEvent> void emit(T domainEvent) {
        List<DomainEventListener> listeners = eventListeners.getOrDefault(
                domainEvent.getClass(), new CopyOnWriteArrayList<>());

        for (DomainEventListener<T> listener : listeners) {
            run(listener, domainEvent);
        }
    }

    /**
     * Runs a listener handle and reacts to the result status returned. When an error is returned we will
     * trigger an error service.
     */
    private <D extends DomainEvent> void run(DomainEventListener<D> listener, D event) {
      Result<?> res = listener.handle(event);
      String listenerName = listener.getClass().getSimpleName();

      if (res.isError()) {
          logger.atError().cause((Exception) res.get()).log("Error running listener {}", listenerName);
      }
    }
}
