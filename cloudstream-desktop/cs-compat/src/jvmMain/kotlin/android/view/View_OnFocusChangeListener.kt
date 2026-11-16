package android.view

/** Android's `View.OnFocusChangeListener`, as far as extension bytecode links against it. */
fun interface OnFocusChangeListener {
    fun onFocusChange(v: View?, hasFocus: Boolean)
}
