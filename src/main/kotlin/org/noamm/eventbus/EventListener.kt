package org.noamm.eventbus

import org.noamm.eventbus.priority.EventPriority
import org.noamm.eventbus.types.IEvent
import org.noamm.eventbus.types.IEventListener

/**
 * A single event listener registration, created via
 * [EventBus.register].
 */
class EventListener<T: IEvent> internal constructor(
    internal val bus: EventBus,
    internal val subscriber: Any,
    internal val eventClass: Class<out IEvent>,
    internal val priority: EventPriority,
    internal val receiveCancelled: Boolean = false,
    internal val callback: EventContext<T>.() -> Unit
): IEventListener<T> {

    @Volatile override var isActive = false

    override fun register(): EventListener<T> {
        if (isActive) return this
        isActive = true
        bus.registerListener(this)
        return this
    }

    override fun unregister(): EventListener<T> {
        if (! isActive) return this
        isActive = false
        bus.unregisterListener(this)
        return this
    }
}