package org.noamm.eventbus.types

import kotlinx.coroutines.Job
import org.noamm.eventbus.EventBus
import org.noamm.eventbus.EventContext
import org.noamm.eventbus.SubscribeEvent
import org.noamm.eventbus.priority.EventPriority

/**
 * The contract implemented by [EventBus].
 */
interface IEventBus: AutoCloseable {
    /**
     * Posts an event to every listener of its class.
     *
     * @return true if the event was canceled.
     */
    fun <T: IEvent> post(event: T): Boolean

    /**
     * Posts an event in the background, ignoring its result.
     */
    fun <T: IEvent> postAsync(event: T): Job

    /**
     * Cancels all pending async work. The bus can still [post] synchronously.
     */
    override fun close()

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
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        callback: EventContext<T>.() -> Unit
    ): IEventListener<T>

    /**
     * Creates a lambda listener for [T].
     */
    fun <T: IEvent> register(
        eventClass: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        callback: EventContext<T>.() -> Unit
    ): IEventListener<T>

    /**
     * Creates and register a lambda listener for [T] that run once.
     */
    fun <T: IEvent> once(
        eventClass: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        callback: EventContext<T>.() -> Unit
    ): IEventListener<T>

    /**
     * Creates an inactive suspending listener for [T].
     * It runs in the background after the post and cannot affect its result.
     */
    fun <T: IEvent> listenerAsync(
        eventClass: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        callback: suspend EventContext<T>.() -> Unit
    ): IEventListener<T>

    /**
     * Creates a suspending listener for [T].
     * It runs in the background after the post and cannot affect its result.
     */
    fun <T: IEvent> registerAsync(
        eventClass: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        callback: suspend EventContext<T>.() -> Unit
    ): IEventListener<T>

    /**
     * Creates and register a suspending listener for [T] that run once.
     */
    fun <T: IEvent> onceAsync(
        eventClass: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        callback: suspend EventContext<T>.() -> Unit
    ): IEventListener<T>
}