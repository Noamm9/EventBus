package org.noamm.eventbus.types

import org.noamm.eventbus.EventListener

/**
 * The contract implemented by [EventListener].
 */
interface IEventListener<T: IEvent> {
    var isActive: Boolean

    /**
     * Activates the listener so it starts receiving events.
     */
    fun register(): EventListener<T>

    /**
     * Deactivates the listener so it stops receiving events.
     */
    fun unregister(): EventListener<T>
}