package org.noamm.ktbus.types

import org.noamm.ktbus.Event

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