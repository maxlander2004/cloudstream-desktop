package android.app

import android.content.Context
import android.content.DialogInterface
import android.view.View

/**
 * Android's `Dialog`.
 *
 * Extensions subclass it (or return one from a `DialogFragment`) for their settings screens.
 * The desktop has no Android window system, so showing it is a no-op and the extension's
 * settings stay at their defaults — but its bytecode links and its dismissal callbacks fire.
 */
open class Dialog(context: Context?) : DialogInterface {
    private val listeners = mutableListOf<DialogInterface.OnDismissListener>()

    open fun setContentView(view: View?) = Unit

    open fun setContentView(layoutResId: Int) = Unit

    open fun setTitle(title: CharSequence?) = Unit

    open fun setTitle(title: Int) = Unit

    open fun setCancelable(cancelable: Boolean) = Unit

    open fun setCanceledOnTouchOutside(cancel: Boolean) = Unit

    open fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        if (listener != null) listeners += listener
    }

    open fun setOnCancelListener(listener: DialogInterface.OnCancelListener?) = Unit

    open fun show() = Unit

    override fun dismiss() {
        listeners.toList().forEach { runCatching { it.onDismiss(this) } }
    }

    override fun cancel() = dismiss()

    open fun isShowing(): Boolean = false
}
