package com.lagradost.cloudstream3.utils

/**
 * Upstream's `Event`, copied from the Android app module.
 *
 * It lives in `app`, not `library`, so extensions compiled against the real app link against
 * this name. Extensions subscribe to app events (`MainActivity.afterPluginsLoadedEvent`) to run
 * setup once the host has finished starting.
 */
class Event<T> {
    private val observers = mutableSetOf<(T) -> Unit>()

    val size: Int get() = observers.size

    operator fun plusAssign(observer: (T) -> Unit) {
        synchronized(observers) {
            observers.add(observer)
        }
    }

    operator fun minusAssign(observer: (T) -> Unit) {
        synchronized(observers) {
            observers.remove(observer)
        }
    }

    operator fun invoke(value: T) {
        synchronized(observers) {
            for (observer in observers)
                observer(value)
        }
    }
}

/** Upstream's `EmptyEvent`: an [Event] with no payload. */
class EmptyEvent {
    private val observers = mutableSetOf<Runnable>()

    val size: Int get() = observers.size

    operator fun plusAssign(observer: Runnable) {
        synchronized(observers) {
            observers.add(observer)
        }
    }

    operator fun minusAssign(observer: Runnable) {
        synchronized(observers) {
            observers.remove(observer)
        }
    }

    operator fun invoke() {
        synchronized(observers) {
            for (observer in observers)
                observer.run()
        }
    }
}
