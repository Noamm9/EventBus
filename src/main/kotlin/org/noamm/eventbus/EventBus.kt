package org.noamm.eventbus

import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import org.noamm.eventbus.priority.EventPriority
import org.noamm.eventbus.priority.PriorityComparator
import org.noamm.eventbus.types.IEvent
import org.noamm.eventbus.types.IEventBus
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class EventBus internal constructor(
    private val exceptionHandler: (Exception) -> Unit,
    dispatcher: CoroutineDispatcher
): IEventBus {
    internal val listeners = ConcurrentHashMap<Class<out IEvent>, List<EventListener<*>>>()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

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
     * Async listeners are launched in parallel once every other listener has
     * run, so they cannot affect the result.
     *
     * @return whether the event was canceled.
     */
    override fun <T: IEvent> post(event: T): Boolean {
        val eventListeners = listeners[event.javaClass] ?: return event.isCanceled
        var context: EventContext<T>? = null

        @Suppress("UNCHECKED_CAST")
        for (listener in eventListeners) try {
            val typedListener = listener as? SyncEventListener<T> ?: continue
            if (event.isCanceled && ! typedListener.receiveCancelled) continue
            val currentContext = context ?: EventContext(event, typedListener).also { context = it }
            currentContext.listener = typedListener
            typedListener.callback.invoke(currentContext)
        }
        catch (exception: Exception) {
            exceptionHandler.invoke(exception)
        }

        // read once before launching, async listeners may still flip it
        val canceled = event.isCanceled

        @Suppress("UNCHECKED_CAST")
        for (listener in eventListeners) {
            val typedListener = listener as? AsyncEventListener<T> ?: continue
            if (canceled && ! typedListener.receiveCancelled) continue
            scope.launch {
                try {
                    typedListener.callback.invoke(EventContext(event, typedListener))
                }
                catch (exception: CancellationException) {
                    throw exception
                }
                catch (exception: Exception) {
                    exceptionHandler.invoke(exception)
                }
            }
        }

        return canceled
    }

    /**
     * Posts the event on the bus dispatcher. Use it when the result does not matter.
     *
     * @return the [Job] running the post, already cancelled if the bus is [closed][close].
     */
    override fun <T: IEvent> postAsync(event: T): Job = scope.launch { post(event) }

    /**
     * Cancels every running async listener and [postAsync] call.
     * Afterward, [post] still runs sync listeners but async work is dropped.
     */
    override fun close() = scope.cancel()

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
        for (eventListeners in listeners.values) {
            for (listener in eventListeners) {
                if (listener.subscriber === subscriber) listener.unregister()
            }
        }
    }

    override fun <T: IEvent> listener(
        eventClass: Class<T>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        callback: EventContext<T>.() -> Unit
    ) = SyncEventListener(this, this, eventClass, priority, receiveCancelled, callback)

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
    ): EventListener<T> {
        val fired = AtomicBoolean()
        return register(eventClass, priority, receiveCancelled) {
            if (! fired.compareAndSet(false, true)) return@register
            listener.unregister()
            callback.invoke(this)
        }
    }

    override fun <T: IEvent> listenerAsync(
        eventClass: Class<T>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        callback: suspend EventContext<T>.() -> Unit
    ) = AsyncEventListener(this, this, eventClass, priority, receiveCancelled, callback)

    override fun <T: IEvent> registerAsync(
        eventClass: Class<T>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        callback: suspend EventContext<T>.() -> Unit
    ) = listenerAsync(eventClass, priority, receiveCancelled, callback).register()

    override fun <T: IEvent> onceAsync(
        eventClass: Class<T>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        callback: suspend EventContext<T>.() -> Unit
    ): EventListener<T> {
        val fired = AtomicBoolean()
        return registerAsync(eventClass, priority, receiveCancelled) {
            if (! fired.compareAndSet(false, true)) return@registerAsync
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
    inline fun <reified T: IEvent> register(
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        noinline callback: EventContext<T>.() -> Unit
    ) = register(T::class.java, priority, receiveCancelled, callback)

    /**
     * Creates an inactive lambda listener for [T].
     *
     * @return [EventListener].
     */
    inline fun <reified T: IEvent> listener(
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        noinline callback: EventContext<T>.() -> Unit
    ) = listener(T::class.java, priority, receiveCancelled, callback)

    /**
     * Creates and register a lambda listener for [T] that run once.
     */
    inline fun <reified T: IEvent> once(
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        noinline callback: EventContext<T>.() -> Unit
    ) = once(T::class.java, priority, receiveCancelled, callback)

    /**
     * Registers a suspending read-only listener for [T] and activates it.
     *
     * @return the listener, so you can keep a reference and
     *         [unregister][EventListener.unregister] it later.
     */
    inline fun <reified T: IEvent> registerAsync(
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        noinline callback: suspend EventContext<T>.() -> Unit
    ) = registerAsync(T::class.java, priority, receiveCancelled, callback)

    /**
     * Creates an inactive suspending read-only listener for [T].
     *
     * @return [EventListener].
     */
    inline fun <reified T: IEvent> listenerAsync(
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        noinline callback: suspend EventContext<T>.() -> Unit
    ) = listenerAsync(T::class.java, priority, receiveCancelled, callback)

    /**
     * Creates and register a suspending read-only listener for [T] that run once.
     */
    inline fun <reified T: IEvent> onceAsync(
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        noinline callback: suspend EventContext<T>.() -> Unit
    ) = onceAsync(T::class.java, priority, receiveCancelled, callback)
}