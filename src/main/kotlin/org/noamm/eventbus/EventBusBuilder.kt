package org.noamm.eventbus

import kotlinx.coroutines.*

/**
 * Returns a new [EventBus] using the builder lambda.
 *
 * A single bus instance is all a project usually needs.
 */
fun bus(builder: EventBusBuilder.() -> Unit = {}) = EventBusBuilder().apply(builder).build()

/**
 * Builder for [EventBus] using the configured handler.
 *
 * A single bus instance is all a project usually needs.
 */
class EventBusBuilder {
    private var errorHandler: (Exception) -> Unit = { throw it }
    private var dispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Sets the handler invoked whenever a listener throws.
     * A rethrowing handler is used by default.
     */
    fun setErrorHandler(handler: (Exception) -> Unit) = apply { this.errorHandler = handler }

    /**
     * Sets the dispatcher async listeners and [EventBus.postAsync] run on.
     * [Dispatchers.Default] is used by default.
     */
    fun setDispatcher(dispatcher: CoroutineDispatcher) = apply { this.dispatcher = dispatcher }

    /**
     * Creates the [EventBus]
     */
    fun build() = EventBus(errorHandler, dispatcher)
}