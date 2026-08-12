package org.noamm.ktbus

class EventContext<T : Event>(val event: T, var listener: EventListener<T>)