package android.os

/**
 * Android's `Looper`, reduced to the main-thread case.
 *
 * Extensions post UI work with `Handler(Looper.getMainLooper())`; on the desktop the caller
 * already runs on the UI thread (or has no UI at all), so posting runs inline.
 */
class Looper private constructor() {
    fun quit() = Unit

    fun quitSafely() = Unit

    fun isCurrentThread(): Boolean = true

    companion object {
        private val main = Looper()

        @JvmStatic
        fun getMainLooper(): Looper = main

        @JvmStatic
        fun myLooper(): Looper? = main
    }
}

/** A message, as much as extensions touch it. */
open class Message {
    var what: Int = 0
    var arg1: Int = 0
    var arg2: Int = 0
    var obj: Any? = null
}

/**
 * Android's `Handler`. Every post runs immediately on the calling thread, which keeps an
 * extension's callback code working without an Android message pump.
 */
open class Handler {
    constructor()

    constructor(looper: Looper?)

    constructor(looper: Looper?, callback: Callback?)

    fun interface Callback {
        fun handleMessage(msg: Message): Boolean
    }

    open fun post(r: Runnable): Boolean = runCatching { r.run() }.isSuccess

    open fun postDelayed(r: Runnable, delayMillis: Long): Boolean = post(r)

    open fun postAtFrontOfQueue(r: Runnable): Boolean = post(r)

    open fun removeCallbacks(r: Runnable) = Unit

    open fun removeCallbacksAndMessages(token: Any?) = Unit

    open fun sendEmptyMessage(what: Int): Boolean = true

    open fun sendMessage(msg: Message): Boolean = true

    open fun sendMessageDelayed(msg: Message, delayMillis: Long): Boolean = true

    open fun obtainMessage(): Message = Message()

    open fun hasMessages(what: Int): Boolean = false

    open fun removeMessages(what: Int) = Unit
}
