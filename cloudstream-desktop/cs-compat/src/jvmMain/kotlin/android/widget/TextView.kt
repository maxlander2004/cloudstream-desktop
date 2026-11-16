package android.widget

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.View
import android.graphics.Typeface
import android.view.accessibility.AccessibilityNodeInfo

open class AndroidTextView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : View() {
    var textValue: CharSequence? = ""
        private set
    var hintValue: CharSequence? = null
        set(v) { field = v }
    var inputTypeValue: Int = 0
        private set
    var enabledValue: Boolean = true
        set(v) { field = v }
    var visibilityValue: Int = View.VISIBLE
        set(v) { field = v }
    var typefaceValue: Typeface? = null
        set(v) { field = v }
    var linesValue: Int = 1
        private set
    var maxLinesValue: Int = Int.MAX_VALUE
        private set
    var minLinesValue: Int = 1
        private set
    var gravityValue: Int = 0
        private set

    override fun setOnClickListener(l: View.OnClickListener?) = Unit
    override fun setOnTouchListener(l: View.OnTouchListener?) = Unit
    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) = Unit
    override fun setBackgroundColor(color: Int) = Unit
    override fun setEnabled(enabled: Boolean) { this.enabledValue = enabled }
    override fun setVisibility(visibility: Int) { this.visibilityValue = visibility }

    open fun setOnFocusChangeListener(l: OnFocusChangeListener?) = Unit
    open fun setOnEditorActionListener(l: OnEditorActionListener?) = Unit
    open fun setTextColor(color: Int) = Unit
    open fun setTypeface(tf: Typeface?, style: Int) = Unit
    open fun setSingleLine(singleLine: Boolean) = Unit
    open fun setLines(lines: Int) { this.linesValue = lines }
    open fun setMaxLines(maxLines: Int) { this.maxLinesValue = maxLines }
    open fun setMinLines(minLines: Int) { this.minLinesValue = minLines }
    open fun setText(text: CharSequence?, type: Int, start: Int, end: Int) {
        this.textValue = text
    }
    open fun setText(text2: CharSequence?) {
        this.textValue = text2
    }
    open fun setText(resId: Int) { }
    open fun setHint(text: CharSequence?) { this.hintValue = text }
    open fun setHint(resId: Int) { }
    open fun setInputType(type: Int) { this.inputTypeValue = type }
    open fun setGravity(gravity: Int) { this.gravityValue = gravity }
    open fun requestFocus(): Boolean = false
    open fun findFocus(): View? = null
    open fun isFocused(): Boolean = false
    open fun isFocusable(): Boolean = true
    open fun isFocusableInTouchMode(): Boolean = true
    open fun setFocusable(focusable: Boolean) { }
    open fun setFocusableInTouchMode(focusable: Boolean) { }
    open fun performClick(): Boolean = false
    open fun performEditorAction(actionId: Int): Boolean = false
    open fun showDropDown() = Unit
    open fun dismissDropDown() = Unit
    open fun isPopupShowing(): Boolean = false
    open fun clearFocus() = Unit
    open fun clearComposingText() = Unit
    open fun onBeginEdit() = Unit
    open fun onEndEdit() = Unit
    open fun isInEditMode(): Boolean = false
    open fun isPressed(): Boolean = false
    open fun setHorizontallyScrolling(scrolling: Boolean) { }
    open fun setBreakStrategy(strategy: Int) { }
    open fun setHyphenationFrequency(frequency: Int) { }
    open fun setJustificationMode(mode: Int) { }
    open fun setEditorExtras(extras: android.os.PersistableBundle?) { }
    open fun setExtractedText(extractedText: AccessibilityNodeInfo?) { }

    companion object {
        const val NO_ID = -1
    }
}

fun interface OnFocusChangeListener {
    fun onFocusChange(v: AndroidTextView?, hasFocus: Boolean)
}

fun interface OnEditorActionListener {
    fun onEditorAction(v: AndroidTextView?, actionId: Int, event: KeyEvent?): Boolean
}
