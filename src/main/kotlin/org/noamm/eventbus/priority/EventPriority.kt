package org.noamm.eventbus.priority

/**
 * Order listeners are invoked for an event, highest first.
 */
enum class EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}