package android.view

/**
 * Android's `View`, as far as extension bytecode links against it.
 *
 * Extensions build Android dialogs and rows when they show a settings screen. The desktop UI
 * is Compose, so the calls are accepted and do nothing.
 */
open class View {
    private var currentVisibility: Int = VISIBLE
    var id: Int = NO_ID

    fun interface OnClickListener {
        fun onClick(v: View?)
    }

    fun interface OnLongClickListener {
        fun onLongClick(v: View?): Boolean
    }

    fun interface OnTouchListener {
        fun onTouch(v: View?, event: Any?): Boolean
    }

    /**
     * Android's `View.OnKeyListener`.
     *
     * Extensions attach one to a search box or a dialog's container to react to Enter. Nothing
     * on the desktop dispatches Android key events, so the listener is stored and never called.
     */
    fun interface OnKeyListener {
        fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean
    }

    open fun setOnClickListener(listener: OnClickListener?) = Unit

    open fun setOnLongClickListener(listener: OnLongClickListener?) = Unit

    open fun setOnTouchListener(listener: OnTouchListener?) = Unit

    open fun setOnKeyListener(listener: OnKeyListener?) = Unit

    open fun setVisibility(visibility: Int) {
        currentVisibility = visibility
    }

    open fun getVisibility(): Int = currentVisibility

    open fun setBackgroundColor(color: Int) = Unit

    open fun setBackgroundResource(resid: Int) = Unit

    open fun setPadding(left: Int, top: Int, right: Int, bottom: Int) = Unit

    open fun setEnabled(enabled: Boolean) = Unit

    open fun getContext(): android.content.Context = android.content.Context()

    open fun findViewById(id: Int): View? = null

    open fun requestLayout() = Unit

    open fun invalidate() = Unit

    open fun setTag(tag: Any?) = Unit

    open fun getTag(): Any? = null

    open fun setLayoutParams(params: Any?) = Unit

    companion object {
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
        const val NO_ID = -1
    }
}
