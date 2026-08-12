package org.noamm.ktbus

abstract class Event(val cancelable: Boolean = false) {
    @Volatile
    open var isCanceled = false
        set(value) {
            if (! cancelable && value) error("tried to cancel an uncancelable event")
            field = value
        }
}