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
import java.util.function.Consumer;


@SuppressWarnings({"rawtypes","unchecked"})
public class DomainEvents {
    private final Map<Class, CopyOnWriteArrayList<DomainEventListener>> eventListeners = new ConcurrentHashMap<>();
    private final Logger logger = LogManager.getLogger(DomainEvents.class);

    private Consumer<Result<? extends  Exception>> errorHandler;

    public DomainEvents() {
        this.errorHandler = (Result<? extends Exception> result) -> {};
    }

    @FunctionalInterface
    public interface DomainEventListener<T extends DomainEvent> {
        Result<?> handle(T event);
    }

    public void onError(Consumer<Result<? extends Exception>> consumer) {
        this.errorHandler = consumer;
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

        // TODO: Don't process the events as soon as we get them. It should the infrastructure layer
        //  calling this layer to process the handlers and in case of failures it can decide how to handle it.
        //  adding the errorHandler is a temporary stop gap so we can allow this method to call a register consumer
        //  to process the errors

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
          this.errorHandler.accept((Result<? extends Exception>) res);
      }
    }
}
