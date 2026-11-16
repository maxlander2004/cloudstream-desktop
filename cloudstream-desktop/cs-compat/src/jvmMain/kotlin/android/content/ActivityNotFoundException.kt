package android.content

/**
 * Android's `ActivityNotFoundException`.
 *
 * Thrown when nothing can handle an intent — an extension catches it to fall back to opening a
 * link in the browser or to tell the user the app is missing. The desktop host's
 * [Context.startActivity] is a no-op rather than a failure, so nothing throws this here; it
 * exists because extension bytecode names it in a catch clause.
 */
class ActivityNotFoundException : RuntimeException {
    constructor() : super()

    constructor(name: String?) : super(name)

    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
