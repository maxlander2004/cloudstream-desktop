package android.view.accessibility

/** Android's `AccessibilityNodeInfo`, as far as extension bytecode links against it. */
open class AccessibilityNodeInfo {
    open fun findAccessibilityNodeInfosByViewId(viewId: String): java.util.List<AccessibilityNodeInfo> {
        val empty = java.util.ArrayList<AccessibilityNodeInfo>()
        @Suppress("UNCHECKED_CAST")
        return empty as java.util.List<AccessibilityNodeInfo>
    }
    open fun findAccessibilityNodeInfosByText(text: String): java.util.List<AccessibilityNodeInfo> {
        val empty = java.util.ArrayList<AccessibilityNodeInfo>()
        @Suppress("UNCHECKED_CAST")
        return empty as java.util.List<AccessibilityNodeInfo>
    }
    open fun findFocus(focusType: Int): AccessibilityNodeInfo? = null
    open fun getParent(): AccessibilityNodeInfo? = null
    open fun getChild(index: Int): AccessibilityNodeInfo? = null
    open fun getChildCount(): Int = 0
    open fun getPackageName(): String? = null
    open fun getClassName(): String? = null
    open fun getContentDescription(): CharSequence? = null
    open fun getText(): CharSequence? = null
    open fun isEnabled(): Boolean = true
    open fun isClickable(): Boolean = false
    open fun isFocusable(): Boolean = false
    open fun isFocused(): Boolean = false
    open fun isSelected(): Boolean = false
    open fun isPassword(): Boolean = false
    open fun isChecked(): Boolean = false
    open fun performAction(action: Int): Boolean = false
    companion object {
        const val FOCUS_INPUT = 1
        const val FOCUS_ACCESSIBILITY = 2
    }
}
