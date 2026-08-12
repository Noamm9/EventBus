package org.noamm.ktbus

class EventListener<T : Event>(
    internal val bus: EventBus,
    val subscriber: Any,
    val eventClass: Class<out Event>,
    val priority: EventPriority,
    val receiveCancelled: Boolean = false,
    val callback: EventContext<T>.() -> Unit
) {
    var isActive = false
        private set

    fun register(): EventListener<T> {
        if (isActive) return this
        isActive = true
        bus.registerListener(this)
        return this
    }

    fun unregister(): EventListener<T> {
        if (!isActive) return this
        isActive = false
        bus.unregisterListener(this)
        return this
    }

    companion object {
        inline fun <reified T : Event> create(
            bus: EventBus,
            subscriber: Any,
            priority: EventPriority = EventPriority.NORMAL,
            receiveCancelled: Boolean = false,
            noinline callback: EventContext<T>.() -> Unit
        ) = EventListener(
            bus = bus,
            subscriber = subscriber,
            eventClass = T::class.java,
            priority = priority,
            receiveCancelled = receiveCancelled,
            callback = callback
        )
    }
}
