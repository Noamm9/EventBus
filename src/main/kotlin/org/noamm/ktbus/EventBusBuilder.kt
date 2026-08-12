package org.noamm.ktbus

import java.util.function.Consumer

class EventBusBuilder {
    private var errorHandler: (Exception) -> Unit = { throw it }

    fun setErrorHandler(handler: (Exception) -> Unit) = apply { this.errorHandler = handler }
    fun setErrorHandler(consumer: Consumer<Exception>) = apply { this.errorHandler = consumer::accept }

    fun build() = EventBus(errorHandler)
}

fun bus(lambda: EventBusBuilder.() -> Unit = {}) = EventBusBuilder().apply(lambda).build()
