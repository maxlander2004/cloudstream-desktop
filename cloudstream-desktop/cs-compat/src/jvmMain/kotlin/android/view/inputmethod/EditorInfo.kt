package android.view.inputmethod

/** Android's `EditorInfo`, as far as extension bytecode links against it. */
class EditorInfo {
    var imeOptions: Int = 0
    var inputType: Int = 0
    var initialActionLabel: CharSequence? = null
    var initialSelStart: Int = -1
    var initialSelEnd: Int = -1
    var privateImeOptions: String? = null
    var actionId: Int = 0
    var actions: IntArray? = null
    var hintText: CharSequence? = null
    var password: Boolean = false
    var refocusable: Boolean = true
    var spellingToolText: CharSequence? = null
    var feedingToken: Int? = null
    var uiOptions: Int = 0

    companion object {
        const val IME_FLAG_NO_FULLSCREEN = 2
        const val IME_FLAG_NO_EXTRACT_UI = 16
        const val IME_ACTION_NONE = 0
        const val IME_ACTION_GO = 1
        const val IME_ACTION_SEARCH = 2
        const val IME_ACTION_SEND = 3
        const val IME_ACTION_NEXT = 4
        const val IME_ACTION_DONE = 5
        const val IME_ACTION_PREVIOUS = 6
        const val IME_FLAG_FORCE_ASCII = 256
        const val IME_FLAG_NAVIGATE_NEXT = 4096
        const val IME_FLAG_NAVIGATE_PREVIOUS = 8192
        const val IME_FLAG_TRAILING_ASCENDING = 65536
        const val IME_FLAG_NO_ACCESSORY_ACTION = 1024
        const val IME_FLAG_NO_PERSONALIZED_LEARNING = 2048
        const val IME_FLAG_CONTINUOUS_SUBTLE_WARNINGS = 512
        const val IME_FLAG_FORCE_ASCII_LOWERCASE = 1048576
        const val IME_CANCEL = 1
    }
}
