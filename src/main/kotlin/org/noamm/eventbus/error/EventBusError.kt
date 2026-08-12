package org.noamm.eventbus.error

/**
 * Base type for every exception thrown by the event bus itself.
 */
abstract class EventBusError(message: String): RuntimeException(message) {
    /**
     * Thrown when trying to cancel an event that is not [cancelable][org.noamm.eventbus.types.IEvent.cancelable].
     */
    class CancelException: EventBusError("tried to cancel an uncancelable event")

    /**
     * Thrown when an annotated subscriber method is not a valid event listener.
     */
    class SubscriptionException(message: String): EventBusError(message)
}