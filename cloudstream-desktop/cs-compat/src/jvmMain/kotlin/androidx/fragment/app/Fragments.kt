package androidx.fragment.app

import android.content.Context
import android.os.Bundle

/**
 * AndroidX's `Fragment`, reduced to the members extensions use.
 *
 * A few extensions show their settings in a `DialogFragment` or read `arguments`. The desktop
 * app has no fragment manager, so a fragment never attaches and `getActivity()` stays null —
 * the same case extension code already guards against.
 */
open class Fragment {
    private var fragmentArguments: Bundle? = null

    open fun getActivity(): FragmentActivity? = null

    open fun getContext(): Context? = getActivity()

    open fun requireContext(): Context = getActivity() ?: Context()

    open fun requireActivity(): FragmentActivity = getActivity() ?: FragmentActivity()

    open fun isAdded(): Boolean = false

    open fun isVisible(): Boolean = false

    open fun getArguments(): Bundle? = fragmentArguments

    open fun setArguments(args: Bundle?) {
        fragmentArguments = args
    }

    open fun getString(resId: Int): String = ""

    open fun getString(resId: Int, vararg formatArgs: Any?): String = ""

    open fun getView(): android.view.View? = null

    open fun dismiss() = Unit
}

/** AndroidX's `DialogFragment`. */
open class DialogFragment : Fragment() {
    open fun show(manager: FragmentManager?, tag: String?) = Unit

    open fun showNow(manager: FragmentManager?, tag: String?) = Unit

    override fun dismiss() = Unit

    open fun dismissAllowingStateLoss() = Unit

    open fun getDialog(): android.app.Dialog? = null

    open fun isCancelable(): Boolean = true

    open fun setCancelable(cancelable: Boolean) = Unit
}

/** AndroidX's `FragmentManager`; nothing is ever added here. */
open class FragmentManager {
    open fun beginTransaction(): FragmentTransaction = FragmentTransaction()

    open fun findFragmentByTag(tag: String?): Fragment? = null

    open fun findFragmentById(id: Int): Fragment? = null

    open fun getFragments(): List<Fragment> = emptyList()

    open fun executePendingTransactions(): Boolean = false

    open fun popBackStack(): Boolean = false

    open fun popBackStackImmediate(): Boolean = false

    open fun getBackStackEntryCount(): Int = 0

    open fun isStateSaved(): Boolean = false

    /**
     * Android's `FragmentManager.FragmentLifecycleCallbacks`.
     *
     * Extensions register one to notice a returned activity's fragments. No fragment is ever
     * added on the desktop, so a registration is accepted and never invoked.
     */
    open class FragmentLifecycleCallbacks {
        open fun onFragmentCreated(
            fm: FragmentManager,
            f: Fragment,
            savedInstanceState: android.os.Bundle?,
        ) = Unit

        open fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) = Unit

        open fun onFragmentResumed(fm: FragmentManager, f: Fragment) = Unit
    }

    open fun registerFragmentLifecycleCallbacks(
        callbacks: FragmentLifecycleCallbacks,
        recursive: Boolean,
    ) = Unit

    open fun unregisterFragmentLifecycleCallbacks(callbacks: FragmentLifecycleCallbacks) = Unit
}

/** AndroidX's `FragmentTransaction`. */
open class FragmentTransaction {
    open fun add(containerViewId: Int, fragment: Fragment, tag: String?): FragmentTransaction = this

    open fun add(fragment: Fragment, tag: String?): FragmentTransaction = this

    open fun replace(containerViewId: Int, fragment: Fragment, tag: String?): FragmentTransaction = this

    open fun remove(fragment: Fragment): FragmentTransaction = this

    open fun show(fragment: Fragment): FragmentTransaction = this

    open fun hide(fragment: Fragment): FragmentTransaction = this

    open fun addToBackStack(name: String?): FragmentTransaction = this

    open fun setCustomAnimations(enter: Int, exit: Int): FragmentTransaction = this

    open fun commit(): Int = 0

    open fun commitAllowingStateLoss(): Int = 0

    open fun commitNow(): Unit = Unit

    open fun commitNowAllowingStateLoss(): Unit = Unit
}

/** AndroidX's `FragmentActivity`: an activity that hosts fragments. */
open class FragmentActivity : android.app.Activity() {
    private val fragments = FragmentManager()

    open fun getSupportFragmentManager(): FragmentManager = fragments

    open fun getFragmentManager(): FragmentManager = fragments
}
