# EventBus

A lightweight Kotlin event bus with listener priorities, event cancellation
and annotation-based subscription.

## Install

**Maven Central**

```kotlin
repositories { mavenCentral() }

dependencies {
    implementation("org.noamm:eventbus:1.0.1")
}
```

## Usage

Define events by extending [`Event`](src/main/kotlin/org/noamm/eventbus/Event.kt):

```kotlin
class PlayerJoinEvent(val name: String) : Event()
class DamageEvent : Event(cancelable = true)
```

Create a bus and post events:

```kotlin
val bus = bus { setErrorHandler { throwable -> throw throwable } }

bus.post(PlayerJoinEvent("Notch"))
bus.post(DamageEvent())
```

### Annotated listeners

Mark methods with `@SubscribeEvent` and register the subscriber object:

```kotlin
val playerTracker = object {
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onJoin(event: PlayerJoinEvent) = println("${event.name} joined")
}

bus.subscribe(playerTracker)
bus.unsubscribe(playerTracker)
```

### Lambda listeners

Register inline listeners for an event class:

```kotlin
bus.register<DamageEvent> { event ->
    event.isCanceled = true
}

bus.once<PlayerJoinEvent> { }
```

`register` returns the [`EventListener`](src/main/kotlin/org/noamm/eventbus/EventListener.kt)
so you can `unregister()` it later.

### Behavior

- Listeners run from [`HIGHEST`](src/main/kotlin/org/noamm/eventbus/priority/EventPriority.kt)
  to `LOWEST` priority.
- Canceling an event skips listeners not registered with `receiveCancelled = true`.
- Exceptions thrown by listeners are routed to the error handler configured on
  the [`EventBusBuilder`](src/main/kotlin/org/noamm/eventbus/EventBusBuilder.kt)
  (rethrown by default).

## License

[CC0 1.0 Universal](LICENSE)