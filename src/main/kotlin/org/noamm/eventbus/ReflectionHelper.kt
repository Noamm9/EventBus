package org.noamm.eventbus

import org.noamm.eventbus.error.EventBusError
import org.noamm.eventbus.types.IEvent
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

internal object ReflectionHelper {
    fun scan(bus: EventBus, subscriber: Any): List<EventListener<*>> = buildList {
        for (method in subscriber::class.java.declaredMethods) {
            val annotation = method.getAnnotation(SubscribeEvent::class.java) ?: continue
            method.isAccessible = true
            add(method.toListener(bus, subscriber, annotation))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Method.toListener(
        bus: EventBus,
        subscriber: Any,
        annotation: SubscribeEvent
    ): EventListener<*> {
        // a suspend fun compiles to an extra trailing Continuation parameter and an Object return type
        val isSuspend = parameterTypes.lastOrNull() == Continuation::class.java
        if (isSuspend && ! annotation.async) throw EventBusError.SubscriptionException("Suspend method $name must be subscribed with async = true.")
        if (parameterCount != if (isSuspend) 2 else 1) throw EventBusError.SubscriptionException("Method $name must have exactly one parameter to be an event listener.")
        if (! isSuspend && returnType != Void.TYPE) throw EventBusError.SubscriptionException("Subscribed method must return Unit/void.")

        val parameterClazz = parameterTypes[0]
        if (parameterClazz.isPrimitive) throw EventBusError.SubscriptionException("Cannot subscribe to a primitive.")
        if (parameterClazz.modifiers and (Modifier.ABSTRACT or Modifier.INTERFACE) != 0) throw EventBusError.SubscriptionException("Cannot subscribe to an abstract class or interface.")
        if (! IEvent::class.java.isAssignableFrom(parameterClazz)) throw EventBusError.SubscriptionException("Parameter must extend ${IEvent::class.simpleName}.")

        val eventClass = parameterClazz as Class<IEvent>
        val callback: EventContext<IEvent>.() -> Unit = {
            try {
                this@toListener.invoke(subscriber, event)
            }
            catch (exception: InvocationTargetException) {
                throw exception.cause ?: exception
            }
        }

        if (! annotation.async) return SyncEventListener(bus, subscriber, eventClass, annotation.priority, annotation.receiveCancelled, callback)
        if (! isSuspend) return AsyncEventListener(bus, subscriber, eventClass, annotation.priority, annotation.receiveCancelled) { callback.invoke(this) }

        return AsyncEventListener(bus, subscriber, eventClass, annotation.priority, annotation.receiveCancelled) {
            suspendCoroutineUninterceptedOrReturn { continuation ->
                try {
                    this@toListener.invoke(subscriber, event, continuation)
                }
                catch (exception: InvocationTargetException) {
                    throw exception.cause ?: exception
                }
            }
        }
    }
}