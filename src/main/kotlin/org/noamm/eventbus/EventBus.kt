package org.noamm.eventbus

import org.noamm.eventbus.priority.EventPriority
import org.noamm.eventbus.priority.PriorityComparator
import org.noamm.eventbus.types.IEvent
import org.noamm.eventbus.types.IEventBus
import java.util.concurrent.*

class EventBus internal constructor(private val exceptionHandler: (Exception) -> Unit): IEventBus {
    internal val listeners = ConcurrentHashMap<Class<out IEvent>, List<EventListener<*>>>()

    internal fun registerListener(listener: EventListener<*>) {
        listeners.compute(listener.eventClass) { _, old ->
            (old.orEmpty() + listener).sortedWith(PriorityComparator)
        }
    }

    internal fun unregisterListener(listener: EventListener<*>) {
        listeners.compute(listener.eventClass) { _, old ->
            old?.filter { it !== listener }?.takeIf(Collection<*>::isNotEmpty)
        }
    }

    /**
     * Posts the event to every listener registered for the event's class.
     *
     * Listeners run from [HIGHEST][EventPriority.HIGHEST] to
     * [LOWEST][EventPriority.LOWEST] priority. Cancelling the event skips
     * listeners that were not registered with `receiveCancelled`.
     *
     * @return whether the event was canceled.
     */
    override fun <T: IEvent> post(event: T): Boolean {
        val eventListeners = listeners[event.javaClass] ?: return event.isCanceled
        var context: EventContext<T>? = null

        @Suppress("UNCHECKED_CAST")
        for (listener in eventListeners) try {
            val typedListener = listener as EventListener<T>
            if (event.isCanceled && ! typedListener.receiveCancelled) continue
            val currentContext = context ?: EventContext(event, typedListener).also { context = it }
            currentContext.listener = typedListener
            typedListener.callback.invoke(currentContext)
        }
        catch (exception: Exception) {
            exceptionHandler.invoke(exception)
        }

        return event.isCanceled
    }

    /**
     * Subscribes every method of [subscriber] marked with [SubscribeEvent]
     * as an event listener.
     */
    override fun subscribe(subscriber: Any) {
        for (listener in ReflectionHelper.scan(this, subscriber)) {
            listener.register()
        }
    }

    /**
     * Unsubscribes every event listener belonging to [subscriber].
     */
    override fun unsubscribe(subscriber: Any) {
        for (eventClass in listeners.keys) {
            listeners.compute(eventClass) { _, old ->
                old?.filterNot { it.subscriber === subscriber }?.takeIf { it.isNotEmpty() }
            }
        }
    }

    override fun <T: IEvent> listener(
        eventClass: Class<T>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        callback: EventContext<T>.() -> Unit
    ) = EventListener(this, this, eventClass, priority, receiveCancelled, callback)

    override fun <T: IEvent> register(
        eventClass: Class<T>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        callback: EventContext<T>.() -> Unit
    ) = listener(eventClass, priority, receiveCancelled, callback).register()

    override fun <T: IEvent> once(
        eventClass: Class<T>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        callback: EventContext<T>.() -> Unit
    ) = register(eventClass, priority, receiveCancelled) {
        listener.unregister()
        callback.invoke(this)
    }
}

/**
 * Registers a lambda as a listener for [T] and activates it.
 *
 * @return the listener, so you can keep a reference and
 *         [unregister][EventListener.unregister] it later.
 */
inline fun <reified T: IEvent> EventBus.register(
    priority: EventPriority = EventPriority.NORMAL,
    receiveCancelled: Boolean = false,
    noinline callback: EventContext<T>.() -> Unit
) = register(T::class.java, priority, receiveCancelled, callback)

/**
 * Creates an inactive lambda listener for [T].
 *
 * @return [EventListener].
 */
inline fun <reified T: IEvent> EventBus.listener(
    priority: EventPriority = EventPriority.NORMAL,
    receiveCancelled: Boolean = false,
    noinline callback: EventContext<T>.() -> Unit
) = listener(T::class.java, priority, receiveCancelled, callback)

/**
 * Creates and register a lambda listener for [T] that run once.
 */
inline fun <reified T: IEvent> EventBus.once(
    priority: EventPriority = EventPriority.NORMAL,
    receiveCancelled: Boolean = false,
    noinline callback: EventContext<T>.() -> Unit
) = once(T::class.java, priority, receiveCancelled, callback)