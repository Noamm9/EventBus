package org.noamm.ktbus

import org.noamm.ktbus.priority.EventPriority

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