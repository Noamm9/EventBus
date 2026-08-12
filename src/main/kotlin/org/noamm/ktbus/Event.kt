package org.noamm.ktbus

import org.noamm.ktbus.error.EventBusError
import org.noamm.ktbus.types.IEvent

/**
 * Base class for everything posted through the bus.
 *
 * @property cancelable whether the event can be canceled.
 */
abstract class Event(override val cancelable: Boolean = false): IEvent {
    @Volatile
    override var isCanceled = false
        set(value) {
            if (! cancelable && value) throw EventBusError.CancelException()
            field = value
        }
}