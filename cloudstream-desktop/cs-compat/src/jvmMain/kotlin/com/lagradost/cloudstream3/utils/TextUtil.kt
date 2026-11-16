package com.lagradost.cloudstream3.utils

import android.content.Context
import androidx.annotation.StringRes

/**
 * Upstream's `UiText`: a string that is either literal or a reference to a string resource.
 *
 * Copied from the Android app module — `VideoClickAction.name` and much of the plugin API are
 * typed with it, so extension bytecode names it constantly. A resource id cannot be resolved on
 * the desktop (the extension's strings live inside its archive, not in a compiled resource
 * table), so [StringResource] resolves to an empty string. That is the same result a missing
 * translation gives upstream, and it keeps the id available for display.
 */
sealed class UiText {
    companion object {
        const val TAG = "UiText"
    }

    data class DynamicString(val value: String) : UiText() {
        override fun toString(): String = value

        override fun equals(other: Any?): Boolean {
            if (other !is DynamicString) return false
            return this.value == other.value
        }

        override fun hashCode(): Int = value.hashCode()
    }

    class StringResource(
        @StringRes val resId: Int,
        val args: List<Any>,
    ) : UiText() {
        override fun toString(): String = "resId = $resId\nargs = ${args.toList()}"

        override fun equals(other: Any?): Boolean {
            if (other !is StringResource) return false
            return this.resId == other.resId && this.args == other.args
        }

        override fun hashCode(): Int {
            var result = resId
            result = 31 * result + args.hashCode()
            return result
        }
    }

    fun asStringNull(context: Context?): String? {
        if (context == null) return null
        return runCatching { asString(context) }.getOrNull()
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> {
            val str = context.getString(resId)
            if (args.isEmpty()) {
                str
            } else {
                str.format(
                    *args.map { if (it is UiText) it.asString(context) else it }.toTypedArray(),
                )
            }
        }
    }
}

fun txt(value: String): UiText = UiText.DynamicString(value)

@JvmName("txtNull")
fun txt(value: String?): UiText? = UiText.DynamicString(value ?: return null)

fun txt(@StringRes resId: Int, vararg args: Any): UiText =
    UiText.StringResource(resId, args.toList())

@JvmName("txtNull")
fun txt(@StringRes resId: Int?, vararg args: Any?): UiText? {
    if (resId == null || args.any { it == null }) return null
    return UiText.StringResource(resId, args.filterNotNull().toList())
}
