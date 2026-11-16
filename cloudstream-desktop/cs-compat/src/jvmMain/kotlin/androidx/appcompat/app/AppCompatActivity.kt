package androidx.appcompat.app

import android.content.Context
import android.view.View
import androidx.fragment.app.FragmentActivity

/**
 * AndroidX's `AppCompatActivity`.
 *
 * Extensions compile against this class even when they only use it as a type — for example
 * `CommonActivity.activity as? AppCompatActivity` before opening their own dialog. On the
 * desktop there is no activity at all ([com.lagradost.cloudstream3.CommonActivity.activity] is
 * null), so this exists for linkage and behaves like an empty activity.
 */
open class AppCompatActivity : FragmentActivity() {
    open fun getSupportActionBar(): ActionBar? = null

    open fun setSupportActionBar(toolbar: Any?) = Unit

    open fun setContentView(view: View?) = Unit

    open fun setContentView(layoutResID: Int) = Unit

    open fun <T : View?> findViewById(id: Int): T? = null

    open fun onBackPressed() = Unit

    open fun onBackPressedDispatcher(): Any? = null

    override fun getApplicationContext(): Context = android.app.Application.instance
}

/** AndroidX's `ActionBar`, which nothing displays on the desktop. */
open class ActionBar {
    private var title: CharSequence? = null

    open fun setTitle(title: CharSequence?) {
        this.title = title
    }

    open fun getTitle(): CharSequence? = title

    open fun setSubtitle(subtitle: CharSequence?) = Unit

    open fun show() = Unit

    open fun hide() = Unit

    open fun setDisplayHomeAsUpEnabled(enabled: Boolean) = Unit

    open fun setDisplayShowHomeEnabled(enabled: Boolean) = Unit

    open fun setDisplayShowTitleEnabled(enabled: Boolean) = Unit
}
