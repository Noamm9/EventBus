package org.noamm.eventbus.types

import org.noamm.eventbus.Event

/**
 * The contract implemented by [Event].
 */
interface IEvent {
    val cancelable: Boolean

    /**
     * Whether the event was canceled.
     */
    var isCanceled: Boolean
}