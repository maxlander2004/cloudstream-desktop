package android.util

import android.content.Context

/**
 * Android's `AttributeSet`, as far as extension bytecode links against it. Most desktop widget
 * code never actually inflates; the common creators that pass nulls are the only bit we need to
 * keep unresolvable without a renderer.
 */
abstract class AttributeSet {
    abstract fun getAttributeCount(): Int
    abstract fun getAttributeName(index: Int): String?
    abstract fun getAttributeValue(index: Int): String?

    companion object {
        fun empty(): AttributeSet = object : AttributeSet() {
            override fun getAttributeCount(): Int = 0
            override fun getAttributeName(index: Int): String? = null
            override fun getAttributeValue(index: Int): String? = null
        }
    }
}

open class TypedArray {
    fun getBoolean(index: Int, defValue: Boolean): Boolean = defValue
    fun getInt(index: Int, defValue: Int): Int = defValue
    fun getString(index: Int?): String? = null
    fun getResourceId(index: Int, defValue: Int): Int = defValue
    fun recycle() = Unit
}

class ResourcesNotFoundException(message: String) : RuntimeException(message)
