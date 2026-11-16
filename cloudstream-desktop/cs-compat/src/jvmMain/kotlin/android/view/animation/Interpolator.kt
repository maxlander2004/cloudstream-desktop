package android.view.animation

/**
 * Android's `Interpolator`, as far as extension bytecode links against it. Returns a linear rate; real
 * timing is handled in Compose, so this exists only to resolve the type.
 */
interface Interpolator {
    fun getInterpolation(input: Float): Float = input.coerceIn(0f, 1f)
}
