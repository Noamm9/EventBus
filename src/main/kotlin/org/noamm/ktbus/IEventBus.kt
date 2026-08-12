package org.noamm.ktbus

interface IEventBus {
    fun <T: Event> post(event: T): Boolean

    fun subscribe(subscriber: Any)

    fun unsubscribe(subscriber: Any)
}