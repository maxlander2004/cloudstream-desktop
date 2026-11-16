package android.widget

import android.view.View

/**
 * Android's `CompoundButton` (the parent of `CheckBox` and `SwitchCompat`), as far as
 * extension bytecode links against it. Real checkbox logic lives in Compose; the compat class
 * simply discards listeners so extension settings code links and does nothing.
 */
open class CompoundButton(context: android.content.Context, attrs: android.util.AttributeSet?, defStyle: Int) : View() {
    var text: CharSequence = ""
        private set

    open fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) = Unit

    open fun isChecked(): Boolean = false

    open fun setChecked(checked: Boolean) = Unit

    fun interface OnCheckedChangeListener {
        fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean)
    }
}
