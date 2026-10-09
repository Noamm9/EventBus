package org.noamm.eventbus

import org.noamm.eventbus.priority.EventPriority
import org.noamm.eventbus.types.IEvent
import org.noamm.eventbus.types.IEventListener

/**
 * A single event listener registration, either a [SyncEventListener]
 * or an [AsyncEventListener].
 */
sealed class EventListener<T: IEvent>(
    internal val bus: EventBus,
    internal val subscriber: Any,
    internal val eventClass: Class<out IEvent>,
    internal val priority: EventPriority,
    internal val receiveCancelled: Boolean
): IEventListener<T> {

    private val lock = Any()
    @Volatile private var active = false

    override var isActive: Boolean
        get() = active
        set(value) {
            if (value) register() else unregister()
        }

    // private lock so the flag and the bus map never disagree, and user code can't contend on it
    override fun register(): EventListener<T> {
        synchronized(lock) {
            if (active) return this
            active = true
            bus.registerListener(this)
        }
        return this
    }

    override fun unregister(): EventListener<T> {
        synchronized(lock) {
            if (! active) return this
            active = false
            bus.unregisterListener(this)
        }
        return this
    }
}