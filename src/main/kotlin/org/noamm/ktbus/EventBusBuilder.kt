package org.noamm.ktbus

class EventBusBuilder {
    private var errorHandler: (Exception) -> Unit = { throw it }

    fun setErrorHandler(handler: (Exception) -> Unit) = apply { this.errorHandler = handler }

    fun build() = EventBus(errorHandler)
}

inline fun bus(builder: EventBusBuilder.() -> Unit) = EventBusBuilder().apply(builder).build()