package org.noamm.eventbus

import org.noamm.eventbus.priority.EventPriority

/**
 * Marks a method as an event listener. The method must take exactly
 * one parameter of a concrete [Event] subtype and return `Unit`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class SubscribeEvent(
    val priority: EventPriority = EventPriority.NORMAL,
    val receiveCancelled: Boolean = false
)