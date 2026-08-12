package org.noamm.ktbus

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object Subscribers {
    fun scan(bus: EventBus, subscriber: Any): List<EventListener<*>> {
        val listeners = mutableListOf<EventListener<*>>()
        for (method in subscriber::class.java.declaredMethods) {
            val annotation = method.getAnnotation(SubscribeEvent::class.java) ?: continue
            method.isAccessible = true

            listeners += EventListener(
                bus = bus,
                subscriber = subscriber,
                eventClass = method.requireEventType(),
                priority = annotation.priority,
                receiveCancelled = annotation.receiveCancelled
            ) {
                try {
                    method.invoke(subscriber, event)
                } catch (exception: InvocationTargetException) {
                    throw exception.cause ?: exception
                }
            }
        }
        return listeners
    }

    private fun Method.requireEventType(): Class<out Event> {
        if (parameterCount < 1) throw IllegalArgumentException(
            "Method $name has no parameters, but it is marked with the @${SubscribeEvent::class.simpleName} annotation. " +
                "Event listeners must be methods with exactly one parameter."
        )

        if (parameterCount > 1) throw IllegalArgumentException("Subscribed method cannot have more than one parameter.")
        if (returnType != Void.TYPE) throw IllegalArgumentException("Subscribed method must be of type 'Void/Unit'.")

        val parameterClazz = parameterTypes[0]
        if (parameterClazz.isPrimitive) throw IllegalArgumentException("Cannot subscribe method to a primitive.")
        if (parameterClazz.modifiers and (Modifier.ABSTRACT or Modifier.INTERFACE) != 0) {
            throw IllegalArgumentException("Cannot subscribe method to an abstract class or interface.")
        }
        if (! Event::class.java.isAssignableFrom(parameterClazz)) {
            throw IllegalArgumentException("Subscribed method parameter must extend ${Event::class.simpleName}.")
        }

        @Suppress("UNCHECKED_CAST")
        return parameterClazz as Class<out Event>
    }
}
