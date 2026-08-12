package org.noamm.ktbus

import org.noamm.ktbus.types.IEvent

/**
 * The context passed to a listener callback: the [event] being posted
 * and the [listener] currently handling it.
 */
class EventContext<T: IEvent>(val event: T, var listener: EventListener<T>)