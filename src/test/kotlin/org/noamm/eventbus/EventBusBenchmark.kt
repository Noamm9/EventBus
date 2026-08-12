package org.noamm.eventbus

import org.junit.jupiter.api.Tag
import kotlin.test.Test

@Tag("benchmark")
class EventBusBenchmark {

    private class BenchEvent: Event(cancelable = true)

    @Test
    fun benchmark() {
        println()
        println("=== EventBus Benchmark (${defaultIterations().toDouble() / 1_000_000.0}M iterations) ===")

        measure("post, 1 listener") { bus, iterations ->
            bus.register<BenchEvent> { }
            repeat(iterations) { bus.post(BenchEvent()) }
        }

        measure("post, 5 listeners") { bus, iterations ->
            repeat(5) { bus.register<BenchEvent> { } }
            repeat(iterations) { bus.post(BenchEvent()) }
        }

        measure("post, 10 listeners") { bus, iterations ->
            repeat(10) { bus.register<BenchEvent> { } }
            repeat(iterations) { bus.post(BenchEvent()) }
        }

        measure("register/unregister") { bus, iterations ->
            var listener = bus.register<BenchEvent> { }
            repeat(iterations) {
                listener = bus.register<BenchEvent> { }.also { listener.unregister() }
            }
            listener.unregister()
        }

        measure("annotated subscriber dispatch") { bus, iterations ->
            val subscriber = Annotated()
            bus.subscribe(subscriber)
            repeat(iterations) { bus.post(BenchEvent()) }
            bus.unsubscribe(subscriber)
        }
    }

    private fun defaultIterations(): Int = 5_000_000

    private fun measure(name: String, workload: (EventBus, Int) -> Unit) {
        val bus = bus()
        val iterations = defaultIterations()

        workload(bus, 100_000)

        val start = System.nanoTime()
        workload(bus, iterations)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        val opsPerSecond = iterations / (elapsedMs / 1000.0)
        val nsPerOp = (elapsedMs * 1_000_000.0) / iterations
        println("$name: ${"%.2f".format(opsPerSecond / 1_000_000.0)}M ops/s ($nsPerOp ns/op)")
    }

    @Suppress("UNUSED_PARAMETER")
    private class Annotated {
        @SubscribeEvent
        fun onBench(event: BenchEvent) {

        }
    }
}