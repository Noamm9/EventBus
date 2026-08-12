package org.noamm.ktbus.types

import org.noamm.ktbus.EventBus
import org.noamm.ktbus.EventContext
import org.noamm.ktbus.SubscribeEvent
import org.noamm.ktbus.priority.EventPriority

/**
 * The contract implemented by [EventBus].
 */
interface IEventBus {
    /**
     * Posts an event to every listener of its class.
     *
     * @return true if the event was canceled.
     */
    fun <T: IEvent> post(event: T): Boolean

    /**
     * Registers every [SubscribeEvent] annotated method on [subscriber].
     */
    fun subscribe(subscriber: Any)

    /**
     * Removes all listeners belonging to [subscriber].
     */
    fun unsubscribe(subscriber: Any)

    /**
     * Creates an inactive lambda listener for [T].
     */
    fun <T: IEvent> listener(
        eventClass: Class<T>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        callback: EventContext<T>.() -> Unit
    ): IEventListener<T>

    /**
     * Creates a lambda listener for [T].
     */
    fun <T: IEvent> register(
        eventClass: Class<T>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        callback: EventContext<T>.() -> Unit
    ): IEventListener<T>

    /**
     * Creates and register a lambda listener for [T] that run once.
     */
    fun <T: IEvent> once(
        eventClass: Class<T>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        callback: EventContext<T>.() -> Unit
    ): IEventListener<T>
}