package org.noamm.eventbus

import org.noamm.eventbus.priority.EventPriority
import org.noamm.eventbus.types.IEvent

/**
 * A suspending read-only listener that runs on the bus dispatcher after every
 * [SyncEventListener], so it cannot affect the post result. Created via [EventBus.registerAsync].
 */
class AsyncEventListener<T: IEvent> internal constructor(
    bus: EventBus,
    subscriber: Any,
    eventClass: Class<out IEvent>,
    priority: EventPriority,
    receiveCancelled: Boolean = false,
    internal val callback: suspend EventContext<T>.() -> Unit
): EventListener<T>(bus, subscriber, eventClass, priority, receiveCancelled)