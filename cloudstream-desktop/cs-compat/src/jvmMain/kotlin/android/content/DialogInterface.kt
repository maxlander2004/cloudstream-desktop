package android.content

/** Android's `DialogInterface`. Extensions implement its listeners for their own dialogs. */
interface DialogInterface {
    fun dismiss()

    fun cancel()

    fun interface OnClickListener {
        fun onClick(dialog: DialogInterface?, which: Int)
    }

    fun interface OnCancelListener {
        fun onCancel(dialog: DialogInterface?)
    }

    fun interface OnDismissListener {
        fun onDismiss(dialog: DialogInterface?)
    }

    fun interface OnShowListener {
        fun onShow(dialog: DialogInterface?)
    }

    fun interface OnMultiChoiceClickListener {
        fun onClick(dialog: DialogInterface?, which: Int, isChecked: Boolean)
    }

    companion object {
        const val BUTTON_POSITIVE = -1
        const val BUTTON_NEGATIVE = -2
        const val BUTTON_NEUTRAL = -3
    }
}
