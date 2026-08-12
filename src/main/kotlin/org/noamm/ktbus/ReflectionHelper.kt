package org.noamm.ktbus

import org.noamm.ktbus.types.IEvent
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

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
        if (parameterCount != 1) throw IllegalArgumentException("Method $name must have exactly one parameter to be an event listener.")
        if (returnType != Void.TYPE) throw IllegalArgumentException("Subscribed method must return Unit/void.")

        val parameterClazz = parameterTypes[0]
        if (parameterClazz.isPrimitive) throw IllegalArgumentException("Cannot subscribe to a primitive.")
        if (parameterClazz.modifiers and (Modifier.ABSTRACT or Modifier.INTERFACE) != 0) {
            throw IllegalArgumentException("Cannot subscribe to an abstract class or interface.")
        }
        if (! IEvent::class.java.isAssignableFrom(parameterClazz)) {
            throw IllegalArgumentException("Parameter must extend ${IEvent::class.simpleName}.")
        }

        return EventListener(
            bus = bus,
            subscriber = subscriber,
            eventClass = parameterClazz as Class<IEvent>,
            priority = annotation.priority,
            receiveCancelled = annotation.receiveCancelled
        ) {
            try {
                this@toListener.invoke(subscriber, event)
            }
            catch (exception: InvocationTargetException) {
                throw exception.cause ?: exception
            }
        }
    }
}