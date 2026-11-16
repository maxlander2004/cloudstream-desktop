package android.animation

import android.view.animation.Interpolator

/**
 * Android's `ValueAnimator`, as far as extension bytecode links against it. Desktop UI is Compose, so
 * animations are no-ops; the class exists so extension bytecode that starts or listens to value
 * animations links and runs without throwing.
 */
open class ValueAnimator() {
    private var _duration: Long = 0L
    private var _startTime: Long = 0L
    private var _currentPlayTime: Long = 0L
    private var _animatedValue: Any? = null
    private var _animatedValueFrom: Any? = null
    private var _animatedValueTo: Any? = null
    private var _repeatCount: Int = 0
    private var _repeatMode: Int = 0
    private var _startDelay: Long = 0L
    private var _started: Boolean = false
    private var _ended: Boolean = false
    private var _canceled: Boolean = false
    private var _paused: Boolean = false
    private var _interpolator: Interpolator? = null

    private val _animatorListener: MutableList<AnimatorListener?> = mutableListOf()
    private val _animatorUpdateListener: MutableList<AnimatorUpdateListener?> = mutableListOf()

    open fun setDuration(duration: Long) { _duration = duration }
    open fun getDuration(): Long = _duration
    open fun setStartTime(startTime: Long) { _startTime = startTime }
    open fun getStartTime(): Long = _startTime
    open fun setCurrentPlayTime(playTime: Long) { _currentPlayTime = playTime }
    open fun getCurrentPlayTime(): Long = _currentPlayTime
    open fun setAnimatedValue(value: Any?) { _animatedValue = value }
    open fun getAnimatedValue(): Any? = _animatedValue
    open fun setAnimatedValueFrom(from: Any?) { _animatedValueFrom = from }
    open fun getAnimatedValueFrom(): Any? = _animatedValueFrom
    open fun setAnimatedValueTo(to: Any?) { _animatedValueTo = to }
    open fun getAnimatedValueTo(): Any? = _animatedValueTo
    open fun setRepeatCount(repeatCount: Int) { _repeatCount = repeatCount }
    open fun getRepeatCount(): Int = _repeatCount
    open fun setRepeatMode(repeatMode: Int) { _repeatMode = repeatMode }
    open fun getRepeatMode(): Int = _repeatMode
    open fun setStartDelay(startDelay: Long) { _startDelay = startDelay }
    open fun getStartDelay(): Long = _startDelay
    open fun setStarted(started: Boolean) { _started = started }
    open fun isStarted(): Boolean = _started
    open fun setEnded(ended: Boolean) { _ended = ended }
    open fun isEnded(): Boolean = _ended
    open fun setCanceled(canceled: Boolean) { _canceled = canceled }
    open fun isCanceled(): Boolean = _canceled
    open fun setPaused(paused: Boolean) { _paused = paused }
    open fun isPaused(): Boolean = _paused
    open fun setInterpolator(i: Interpolator?) { _interpolator = i }
    open fun getInterpolator(): Interpolator? = _interpolator
    open fun setEvaluator(evaluator: Any?) { }
    open fun <T> getAnimatedValue(type: Class<T>): T? = _animatedValue as? T
    open fun addUpdateListener(listener: AnimatorUpdateListener?) { _animatorUpdateListener.add(listener) }
    open fun removeUpdateListener(listener: AnimatorUpdateListener?) { _animatorUpdateListener.remove(listener) }
    open fun removeAllUpdateListeners() { _animatorUpdateListener.clear() }
    open fun addListener(listener: AnimatorListener?) { _animatorListener.add(listener) }
    open fun removeListener(listener: AnimatorListener?) { _animatorListener.remove(listener) }
    open fun removeAllListeners() { _animatorListener.clear() }
    open fun setListener(listener: AnimatorListener?) { _animatorListener.add(listener) }
    open fun isRunning(): Boolean = _started && !_ended
    open fun setHandleNullView(_v: Boolean) { }
    open fun setCurrentPlayTimeAndUpdate(playTime: Long) { _currentPlayTime = playTime }
    open fun getAnimatedFraction(): Float = if (_duration <= 0L) 1f else (_currentPlayTime.toFloat() / _duration).coerceIn(0f, 1f)
    open fun start() { _started = true; _ended = false; _canceled = false; _paused = false }
    open fun startNow() { start() }
    open fun cancel() { _canceled = true; _ended = true; _started = false }
    open fun end() { _ended = true; _started = false }
    open fun pause() { _paused = true }
    open fun resume() { _paused = false }
    open fun reverse() { }
    open fun isReverse(): Boolean = false
    open fun getAnimatedValueSize(): Int = 0
    open fun getAnimatedObject(): Any? = null
    open fun getAnimatorUpdateListeners(): java.util.List<AnimatorUpdateListener> =
        _animatorUpdateListener.toList() as java.util.List<AnimatorUpdateListener>
    open fun getListeners(): java.util.List<AnimatorListener> =
        _animatorListener.toList() as java.util.List<AnimatorListener>
    open fun clone(): ValueAnimator = ValueAnimator()
    open fun getMinimumDrawDuration(): Long = 0L
    open fun setMinimumDrawDuration(d: Long) { }
    open fun getChangingConfigurations(): Int = 0
    open fun setChangingConfigurations(config: Int) { }

    companion object {
        const val INFINITE = -1

        @JvmStatic
        fun animatorUpdateListenerClass(): Class<AnimatorUpdateListener> =
            AnimatorUpdateListener::class.java
    }

    /**
     * Declared inside the class on purpose: on Android this is the nested interface
     * `android.animation.ValueAnimator$AnimatorUpdateListener`, and that binary name is what
     * extension bytecode implements and passes to [addUpdateListener].
     */
    fun interface AnimatorUpdateListener {
        fun onAnimationUpdate(animation: ValueAnimator?)
    }
}

interface AnimatorListener {
    fun onAnimationStart(animation: ValueAnimator?)
    fun onAnimationEnd(animation: ValueAnimator?)
    fun onAnimationCancel(animation: ValueAnimator?)
    fun onAnimationRepeat(animation: ValueAnimator?)
}

interface AnimatorPauseListener {
    fun onAnimationPause(animation: ValueAnimator?)
    fun onAnimationResume(animation: ValueAnimator?)
}

interface AnimatorDestroyListener {
    fun onAnimationDestroy(animation: ValueAnimator?)
}
