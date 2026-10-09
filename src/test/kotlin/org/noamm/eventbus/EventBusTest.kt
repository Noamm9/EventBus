@file:Suppress("unused")

package org.noamm.eventbus

import kotlinx.coroutines.*
import org.noamm.eventbus.error.EventBusError.CancelException
import org.noamm.eventbus.error.EventBusError.SubscriptionException
import org.noamm.eventbus.priority.EventPriority
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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

        assertFailsWith<CancelException> { event.isCanceled = true }
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
    fun `once runs exactly once then unregisters`() {
        val bus = bus()
        var count = 0
        bus.once<PlainEvent> { count ++ }

        bus.post(PlainEvent())
        bus.post(PlainEvent())

        assertEquals(1, count)
        assertEquals(0, bus.listeners[PlainEvent::class.java]?.size ?: 0)
    }

    @Test
    fun `once with receiveCancelled still fires after cancellation`() {
        val bus = bus()
        var count = 0

        bus.register<CancelableEvent>(priority = EventPriority.HIGHEST) { event.isCanceled = true }
        bus.once<CancelableEvent>(receiveCancelled = true) { count ++ }

        bus.post(CancelableEvent())

        assertEquals(1, count)
    }

    @Test
    fun `once unregisters even when the callback throws`() {
        val errors = mutableListOf<Exception>()
        val bus = EventBusBuilder()
            .setErrorHandler { errors.add(it) }
            .build()
        bus.once<PlainEvent> { throw IllegalStateException("boom") }

        bus.post(PlainEvent())
        bus.post(PlainEvent())

        assertEquals(1, errors.size)
        assertEquals("boom", errors[0].message)
    }

    @Test
    fun `unsubscribing an unknown subscriber is a no-op`() {
        val bus = bus()
        var count = 0
        bus.register<PlainEvent> { count ++ }

        bus.unsubscribe(Any())
        bus.post(PlainEvent())

        assertEquals(1, count)
    }

    @Test
    fun `unsubscribe does not disturb other listener classes`() {
        class Subscriber {
            @SubscribeEvent
            fun onPlain(event: PlainEvent) {
            }
        }

        val bus = bus()
        val subscriber = Subscriber()
        bus.subscribe(subscriber)
        var lambdaCount = 0
        bus.register<CancelableEvent> { lambdaCount ++ }

        bus.unsubscribe(subscriber)
        bus.post(PlainEvent())
        bus.post(CancelableEvent())

        assertEquals(1, lambdaCount)
    }

    @Test
    fun `unregistering a never-registered listener is a no-op`() {
        val bus = bus()
        var count = 0
        bus.register<PlainEvent> { count ++ }
        bus.unregisterListener(SyncEventListener<PlainEvent>(bus, this, PlainEvent::class.java, EventPriority.NORMAL) {})

        bus.post(PlainEvent())

        assertEquals(1, count)
    }

    @Test
    fun `unregistering for an empty event class is a no-op`() {
        val bus = bus()
        bus.unregisterListener(SyncEventListener<PlainEvent>(bus, this, PlainEvent::class.java, EventPriority.NORMAL) {})

        assertEquals(0, bus.listeners[PlainEvent::class.java]?.size ?: 0)
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

        class PrimitiveParam {
            @SubscribeEvent
            fun onEvent(event: Int) {
            }
        }

        val bus = bus()
        assertFailsWith<SubscriptionException> { bus.subscribe(NoParams()) }
        assertFailsWith<SubscriptionException> { bus.subscribe(TooManyParams()) }
        assertFailsWith<SubscriptionException> { bus.subscribe(NonUnitReturn()) }
        assertFailsWith<SubscriptionException> { bus.subscribe(NonEventParam()) }
        assertFailsWith<SubscriptionException> { bus.subscribe(AbstractParam()) }
        assertFailsWith<SubscriptionException> { bus.subscribe(PrimitiveParam()) }
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

    @Test
    fun `async listener runs after sync listeners on another thread`() {
        val bus = bus()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val asyncThread = CompletableDeferred<Thread>()

        bus.registerAsync<PlainEvent>(priority = EventPriority.HIGHEST) {
            order += "async"
            asyncThread.complete(Thread.currentThread())
        }
        bus.register<PlainEvent> { order += "sync" }

        bus.post(PlainEvent())

        runBlocking { assertNotEquals(Thread.currentThread(), withTimeout(5_000) { asyncThread.await() }) }
        assertEquals(listOf("sync", "async"), order)
    }

    @Test
    fun `async listener cannot change the post result`() {
        val bus = bus()
        bus.registerAsync<CancelableEvent> { event.isCanceled = true }

        repeat(1_000) { assertFalse(bus.post(CancelableEvent())) }
    }

    @Test
    fun `async listener can suspend`() {
        val bus = bus()
        val done = CompletableDeferred<Unit>()
        bus.registerAsync<PlainEvent> {
            delay(10)
            done.complete(Unit)
        }

        bus.post(PlainEvent())

        runBlocking { withTimeout(5_000) { done.await() } }
    }

    @Test
    fun `annotated async methods receive the event`() {
        class Subscriber {
            val suspendReceived = CompletableDeferred<PlainEvent>()
            val plainReceived = CompletableDeferred<PlainEvent>()

            @SubscribeEvent(async = true)
            suspend fun onSuspend(event: PlainEvent) {
                delay(10)
                suspendReceived.complete(event)
            }

            @SubscribeEvent(async = true)
            fun onPlain(event: PlainEvent) {
                plainReceived.complete(event)
            }
        }

        val bus = bus()
        val subscriber = Subscriber()
        bus.subscribe(subscriber)

        val event = PlainEvent()
        bus.post(event)

        runBlocking {
            withTimeout(5_000) {
                assertEquals(event, subscriber.suspendReceived.await())
                assertEquals(event, subscriber.plainReceived.await())
            }
        }
    }

    @Test
    @Suppress("UNUSED_PARAMETER")
    fun `annotated suspend listener routes original exception to handler`() {
        class Subscriber {
            @SubscribeEvent(async = true)
            suspend fun beforeSuspending(event: PlainEvent) {
                throw IllegalStateException("before")
            }

            @SubscribeEvent(async = true)
            suspend fun afterSuspending(event: PlainEvent) {
                delay(1)
                throw IllegalStateException("after")
            }
        }

        val errors = Collections.synchronizedList(mutableListOf<Exception>())
        val latch = CountDownLatch(2)
        val bus = EventBusBuilder()
            .setErrorHandler { errors.add(it); latch.countDown() }
            .build()
        bus.subscribe(Subscriber())

        bus.post(PlainEvent())

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(setOf("before", "after"), errors.map { it.message }.toSet())
        assertTrue(errors.all { it is IllegalStateException })
    }

    @Test
    @Suppress("UNUSED_PARAMETER")
    fun `subscribing invalid suspend methods throws`() {
        class NotAsync {
            @SubscribeEvent
            suspend fun onEvent(event: PlainEvent) {
            }
        }

        class NoParams {
            @SubscribeEvent(async = true)
            suspend fun onEvent() {
            }
        }

        val bus = bus()
        assertFailsWith<SubscriptionException> { bus.subscribe(NotAsync()) }
        assertFailsWith<SubscriptionException> { bus.subscribe(NoParams()) }
    }

    @Test
    fun `async listeners respect receiveCancelled`() {
        val executor = Executors.newSingleThreadExecutor()
        executor.asCoroutineDispatcher().use { dispatcher ->
            val bus = EventBusBuilder().setDispatcher(dispatcher).build()
            val order = Collections.synchronizedList(mutableListOf<String>())

            bus.register<CancelableEvent>(priority = EventPriority.HIGHEST) { event.isCanceled = true }
            bus.registerAsync<CancelableEvent> { order += "blocked" }
            bus.registerAsync<CancelableEvent>(receiveCancelled = true) { order += "receiver" }

            assertTrue(bus.post(CancelableEvent()))
            executor.submit { }.get(5, TimeUnit.SECONDS)

            assertEquals(listOf("receiver"), order)
        }
    }

    @Test
    fun `async listener exception routes to handler`() {
        val error = CompletableDeferred<Exception>()
        val bus = EventBusBuilder()
            .setErrorHandler { error.complete(it) }
            .build()
        bus.registerAsync<PlainEvent> { throw IllegalStateException("boom") }

        bus.post(PlainEvent())

        runBlocking { assertEquals("boom", withTimeout(5_000) { error.await() }.message) }
    }

    @Test
    fun `once and onceAsync fire exactly once under concurrent posts`() {
        val executor = Executors.newSingleThreadExecutor()
        executor.asCoroutineDispatcher().use { dispatcher ->
            val bus = EventBusBuilder().setDispatcher(dispatcher).build()
            val syncCount = AtomicInteger()
            val asyncCount = AtomicInteger()
            bus.once<BusEvent> { syncCount.incrementAndGet() }
            bus.onceAsync<BusEvent> { asyncCount.incrementAndGet() }

            val start = CountDownLatch(1)
            val threads = (0 until 8).map {
                Thread { start.await(); repeat(1_000) { bus.post(BusEvent()) } }
            }
            threads.forEach { it.start() }
            start.countDown()
            threads.forEach { it.join() }
            executor.submit { }.get(5, TimeUnit.SECONDS)

            assertEquals(1, syncCount.get())
            assertEquals(1, asyncCount.get())
            assertEquals(0, bus.listeners[BusEvent::class.java]?.size ?: 0)
        }
    }

    @Test
    fun `postAsync job completes after listeners run`() {
        val bus = bus()
        var count = 0
        bus.register<PlainEvent> { count ++ }

        runBlocking { bus.postAsync(PlainEvent()).join() }

        assertEquals(1, count)
    }

    @Test
    fun `close cancels running async listeners without reporting an error`() {
        val errors = Collections.synchronizedList(mutableListOf<Exception>())
        val bus = EventBusBuilder()
            .setErrorHandler { errors.add(it) }
            .build()
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        bus.registerAsync<PlainEvent> {
            started.complete(Unit)
            try {
                awaitCancellation()
            }
            finally {
                cancelled.complete(Unit)
            }
        }

        bus.post(PlainEvent())
        runBlocking { withTimeout(5_000) { started.await() } }
        bus.close()

        runBlocking { withTimeout(5_000) { cancelled.await() } }
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `close drops async work but keeps sync post`() {
        val executor = Executors.newSingleThreadExecutor()
        executor.asCoroutineDispatcher().use { dispatcher ->
            val bus = EventBusBuilder().setDispatcher(dispatcher).build()
            var syncCount = 0
            val asyncRan = AtomicBoolean()
            bus.register<PlainEvent> { syncCount ++ }
            bus.registerAsync<PlainEvent> { asyncRan.set(true) }

            bus.close()
            bus.post(PlainEvent())
            val job = bus.postAsync(PlainEvent())
            executor.submit { }.get(5, TimeUnit.SECONDS)

            assertTrue(job.isCancelled)
            assertEquals(1, syncCount)
            assertFalse(asyncRan.get())
        }
    }

    @Test
    fun `async work runs on the configured dispatcher`() {
        Executors.newSingleThreadExecutor { Thread(it, "bus-worker") }.asCoroutineDispatcher().use { dispatcher ->
            val bus = EventBusBuilder().setDispatcher(dispatcher).build()
            val threadName = CompletableDeferred<String>()
            bus.registerAsync<PlainEvent> { threadName.complete(Thread.currentThread().name) }

            bus.post(PlainEvent())

            runBlocking { assertTrue(withTimeout(5_000) { threadName.await() }.startsWith("bus-worker")) }
        }
    }

    @Test
    fun `concurrent register adds the listener once`() {
        val bus = bus()
        val listener = bus.listener<BusEvent> { }

        val start = CountDownLatch(1)
        val threads = (0 until 8).map { Thread { start.await(); listener.register() } }
        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join() }

        assertEquals(1, bus.listeners[BusEvent::class.java]?.size)
    }

    @Test
    fun `concurrent register and unregister keep isActive consistent with the bus`() {
        val bus = bus()
        val listener = bus.listener<BusEvent> { }

        val registering = Thread { repeat(10_000) { listener.register() } }
        val unregistering = Thread { repeat(10_000) { listener.unregister() } }
        registering.start()
        unregistering.start()
        registering.join()
        unregistering.join()

        val eventListeners = bus.listeners[BusEvent::class.java].orEmpty()
        assertTrue(eventListeners.size <= 1)
        assertEquals(listener.isActive, listener in eventListeners)
    }

    @Test
    fun `setting isActive registers and unregisters`() {
        val bus = bus()
        var count = 0
        val listener = bus.register<PlainEvent> { count ++ }

        listener.isActive = false
        bus.post(PlainEvent())
        listener.isActive = true
        bus.post(PlainEvent())

        assertEquals(1, count)
    }

    @Test
    fun `unsubscribe deactivates listeners so they can be registered again`() {
        val bus = bus()
        var count = 0
        val listener = bus.register<PlainEvent> { count ++ }

        bus.unsubscribe(bus)
        assertFalse(listener.isActive)
        listener.register()
        bus.post(PlainEvent())

        assertEquals(1, count)
    }

    private class EventClass: Event()

    private class BusEvent: Event()

    private class SubscriberWithEvent {
        @SubscribeEvent
        fun onEvent(event: EventClass) {
        }
    }
}
