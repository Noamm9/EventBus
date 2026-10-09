package org.noamm.eventbus

import org.noamm.eventbus.priority.EventPriority
import org.noamm.eventbus.types.IEvent

/**
 * A listener that runs on the posting thread and can cancel or modify the event,
 * created via [EventBus.register].
 */
class SyncEventListener<T: IEvent> internal constructor(
    bus: EventBus,
    subscriber: Any,
    eventClass: Class<out IEvent>,
    priority: EventPriority,
    receiveCancelled: Boolean = false,
    internal val callback: EventContext<T>.() -> Unit
): EventListener<T>(bus, subscriber, eventClass, priority, receiveCancelled)