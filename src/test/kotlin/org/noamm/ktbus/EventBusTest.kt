package org.noamm.ktbus

import org.noamm.ktbus.priority.EventPriority
import kotlin.test.*


class EventBusTest {

    private class CancelableEvent: Event(cancelable = true)
    private class PlainEvent: Event()

    @Test
    fun `post invokes registered listener with the event`() {
        val bus = bus()
        var received: CancelableEvent? = null
        bus.register<CancelableEvent> { received = event }

        val posted = CancelableEvent()
        bus.post(posted)

        assertEquals(posted, received)
    }

    @Test
    fun `post returns false when event is not canceled`() {
        val bus = bus()
        bus.register<PlainEvent> {}

        assertFalse(bus.post(PlainEvent()))
    }

    @Test
    fun `post returns true when event is canceled`() {
        val bus = bus()
        bus.register<CancelableEvent> { event.isCanceled = true }

        assertTrue(bus.post(CancelableEvent()))
    }

    @Test
    fun `listeners run in priority order`() {
        val bus = bus()
        val order = mutableListOf<String>()

        bus.register<PlainEvent>(priority = EventPriority.NORMAL) { order += "NORMAL" }
        bus.register<PlainEvent>(priority = EventPriority.HIGHEST) { order += "HIGHEST" }
        bus.register<PlainEvent>(priority = EventPriority.LOWEST) { order += "LOWEST" }
        bus.register<PlainEvent>(priority = EventPriority.HIGH) { order += "HIGH" }
        bus.register<PlainEvent>(priority = EventPriority.LOW) { order += "LOW" }

        bus.post(PlainEvent())

        assertEquals(listOf("HIGHEST", "HIGH", "NORMAL", "LOW", "LOWEST"), order)
    }

    @Test
    fun `cancellation skips listeners that do not receive cancelled events`() {
        val bus = bus()
        val order = mutableListOf<String>()

        bus.register<CancelableEvent>(priority = EventPriority.HIGHEST) { order += "canceler"; event.isCanceled = true }
        bus.register<CancelableEvent> { order += "blocked" }
        bus.register<CancelableEvent>(receiveCancelled = true) { order += "receiver" }

        assertTrue(bus.post(CancelableEvent()))
        assertEquals(listOf("canceler", "receiver"), order)
    }

    @Test
    fun `setting isCanceled on a non-cancelable event throws`() {
        val event = PlainEvent()

        assertFailsWith<IllegalStateException> { event.isCanceled = true }
    }

    @Test
    fun `annotated subscriber receives and unsubscribes`() {
        class Subscriber {
            var count = 0
            var last: CancelableEvent? = null

            @SubscribeEvent
            fun onCancelable(event: CancelableEvent) {
                count ++
                last = event
            }
        }

        val bus = bus()
        val subscriber = Subscriber()
        bus.subscribe(subscriber)

        val event = CancelableEvent()
        bus.post(event)

        assertEquals(1, subscriber.count)
        assertEquals(event, subscriber.last)

        bus.unsubscribe(subscriber)
        bus.post(CancelableEvent())

        assertEquals(1, subscriber.count)
    }

    @Test
    fun `unregister stops delivery and reregister works`() {
        val bus = bus()
        var count = 0
        val listener = bus.register<PlainEvent> { count ++ }

        bus.post(PlainEvent())
        listener.unregister()
        bus.post(PlainEvent())
        listener.register()
        bus.post(PlainEvent())

        assertEquals(2, count)
    }

    @Test
    fun `registering the same listener twice is idempotent`() {
        val bus = bus()
        var count = 0
        val listener = bus.register<PlainEvent> { count ++ }

        listener.register()
        bus.post(PlainEvent())

        assertEquals(1, count)
    }

    @Test
    @Suppress("UNUSED_PARAMETER")
    fun `subscribing invalid methods throws`() {
        class NoParams {
            @SubscribeEvent
            fun onEvent() {
            }
        }

        class TooManyParams {
            @SubscribeEvent
            fun onEvent(a: PlainEvent, b: PlainEvent) {
            }
        }

        class NonUnitReturn {
            @SubscribeEvent
            fun onEvent(event: PlainEvent): Int = 1
        }

        class NonEventParam {
            @SubscribeEvent
            fun onEvent(value: String) {
            }
        }

        class AbstractParam {
            @SubscribeEvent
            fun onEvent(event: Event) {
            }
        }

        val bus = bus()
        assertFailsWith<IllegalArgumentException> { bus.subscribe(NoParams()) }
        assertFailsWith<IllegalArgumentException> { bus.subscribe(TooManyParams()) }
        assertFailsWith<IllegalArgumentException> { bus.subscribe(NonUnitReturn()) }
        assertFailsWith<IllegalArgumentException> { bus.subscribe(NonEventParam()) }
        assertFailsWith<IllegalArgumentException> { bus.subscribe(AbstractParam()) }
    }

    @Test
    fun `throwing listener routes to exception handler and other listeners still run`() {
        val errors = mutableListOf<Exception>()
        val bus = EventBusBuilder()
            .setErrorHandler { errors.add(it) }
            .build()

        val order = mutableListOf<String>()

        bus.register<PlainEvent> { order += "first" }
        bus.register<PlainEvent> { throw IllegalStateException("boom") }
        bus.register<PlainEvent> { order += "third" }

        bus.post(PlainEvent())

        assertEquals(listOf("first", "third"), order)
        assertEquals(1, errors.size)
        assertEquals("boom", errors[0].message)
    }

    @Test
    fun `annotated throwing listener routes original exception to handler`() {
        class Subscriber {
            @SubscribeEvent
            fun onEvent(event: PlainEvent) {
                throw IllegalStateException("boom")
            }
        }

        val errors = mutableListOf<Exception>()
        val bus = EventBusBuilder()
            .setErrorHandler { errors.add(it) }
            .build()
        bus.subscribe(Subscriber())

        bus.post(PlainEvent())

        assertEquals(1, errors.size)
        assertEquals(IllegalStateException::class, errors[0]::class)
        assertEquals("boom", errors[0].message)
    }

    @Test
    fun `post without listeners returns false`() {
        val bus = bus()

        assertFalse(bus.post(CancelableEvent()))
    }

    @Test
    fun `context listener matches the currently dispatched listener`() {
        val bus = bus()
        val seen = mutableListOf<EventListener<*>>()

        val first = bus.register<CancelableEvent>(priority = EventPriority.HIGHEST) { seen += listener }
        val second = bus.register<CancelableEvent> { seen += listener }

        bus.post(CancelableEvent())

        assertEquals(listOf<EventListener<*>>(first, second), seen)
    }

    @Test
    fun `post only dispatches to listeners for the exact event class`() {
        val bus = bus()
        var plainCount = 0
        var cancelableCount = 0

        bus.register<PlainEvent> { plainCount ++ }
        bus.register<CancelableEvent> { cancelableCount ++ }

        bus.post(PlainEvent())
        bus.post(CancelableEvent())

        assertEquals(1, plainCount)
        assertEquals(1, cancelableCount)
    }

    @Test
    fun `concurrent registration on same event class loses no listeners`() {
        val bus = bus()
        val threadCount = 4
        val registersPerThread = 100

        val threads = (0 until threadCount).map {
            Thread { repeat(registersPerThread) { bus.register<BusEvent> { } } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val listeners = bus.listeners[BusEvent::class.java] ?: emptyList()
        assertEquals(threadCount * registersPerThread, listeners.size)
    }

    @Test
    fun `concurrent register and post stays consistent`() {
        val bus = bus()
        val listenerCount = 100

        val registerThread = Thread { repeat(listenerCount) { bus.register<BusEvent> { } } }
        registerThread.start()

        val postThread = Thread { repeat(10_000) { bus.post(BusEvent()) } }
        postThread.start()

        registerThread.join()
        postThread.join()

        val listeners = bus.listeners[BusEvent::class.java] ?: emptyList()
        assertEquals(listenerCount, listeners.size)
    }

    @Test
    fun `concurrent unsubscribe leaves no stale listeners`() {
        val bus = bus()
        val subscriberCount = 20
        val subscribers = (0 until subscriberCount).map { SubscriberWithEvent() }
        subscribers.forEach { bus.subscribe(it) }

        val threads = subscribers.map { subscriber ->
            Thread { bus.unsubscribe(subscriber) }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        for ((_, eventListeners) in bus.listeners) {
            assertEquals(0, eventListeners.size)
        }
    }

    private class EventClass: Event()

    private class BusEvent: Event()

    private class SubscriberWithEvent {
        @SubscribeEvent
        fun onEvent(event: EventClass) {
        }
    }
}
