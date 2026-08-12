package org.noamm.ktbus

import java.util.concurrent.*

class EventBus internal constructor(private val exceptionHandler: (Exception) -> Unit): IEventBus {
    internal val listeners = ConcurrentHashMap<Class<out Event>, List<EventListener<*>>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T: Event> post(event: T): Boolean {
        val eventListeners = listeners[event.javaClass] ?: return event.isCanceled
        var context: EventContext<T>? = null

        for (listener in eventListeners) {
            val typedListener = listener as EventListener<T>
            try {
                if (event.isCanceled && ! typedListener.receiveCancelled) continue
                val currentContext = context ?: EventContext(event, typedListener).also { context = it }
                currentContext.listener = typedListener
                typedListener.callback.invoke(currentContext)
            }
            catch (exception: Exception) {
                exceptionHandler.invoke(exception)
            }
        }

        return event.isCanceled
    }

    override fun subscribe(subscriber: Any) {
        for (listener in Subscribers.scan(this, subscriber)) {
            listener.register()
        }
    }

    override fun unsubscribe(subscriber: Any) {
        for (eventClass in listeners.keys) {
            listeners.compute(eventClass) { _, old ->
                old?.filterNot { it.subscriber === subscriber }?.takeIf { it.isNotEmpty() }
            }
        }
    }

    inline fun <reified T: Event> register(
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        noinline callback: EventContext<T>.() -> Unit
    ): EventListener<T> = EventListener.create(
        bus = this,
        subscriber = this,
        priority = priority,
        receiveCancelled = receiveCancelled,
        callback = callback
    ).register()

    internal fun registerListener(listener: EventListener<*>) {
        listeners.compute(listener.eventClass) { _, old ->
            (old.orEmpty() + listener).sortedBy { it.priority.ordinal }
        }
    }

    internal fun unregisterListener(listener: EventListener<*>) {
        listeners.compute(listener.eventClass) { _, old ->
            old?.filter { it !== listener }?.takeIf(Collection<*>::isNotEmpty)
        }
    }
}