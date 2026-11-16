package com.lagradost.cloudstream3

import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.utils.Event

/**
 * Upstream's `MainActivity`, reduced to the members extensions reach for.
 *
 * On Android this is the whole app shell; extensions only ever touch its companion. The most
 * common use is subscribing to [afterPluginsLoadedEvent] so a provider can apply the user's
 * settings after the host has started. The desktop host is headless and has no activity, but
 * the class has to exist with that companion or the extension dies with
 * `ClassNotFoundException: com.lagradost.cloudstream3.MainActivity` before its plugin class is
 * even constructed.
 *
 * Note the file name: the `library` sources already compile `MainActivity.kt` (whose top-level
 * `app`/`insecureApp` become `MainActivityKt`), so this class lives in its own file to avoid
 * clashing facade classes.
 */
open class MainActivity : AppCompatActivity() {
    companion object {
        /**
         * Fires once the host has finished loading the plugins of a batch.
         *
         * Extensions that register their providers from here (rather than from `load()`) only
         * work if the host fires it: `LegacyExtensions.notifyPluginsLoaded` does, after the
         * desktop's startup load loop. The Boolean means "force reload" upstream.
         */
        val afterPluginsLoadedEvent = Event<Boolean>()

        /** Fires when the provider (home page) list itself is ready. */
        val mainPluginsLoadedEvent = Event<Boolean>()

        /** Fires when the repository list has been loaded. */
        val afterRepositoryLoadedEvent = Event<Boolean>()

        /** Fires when the user's bookmarks changed, so open screens refresh. */
        val bookmarksUpdatedEvent = Event<Boolean>()

        /** Fires when home has to be rebuilt, e.g. after switching accounts. */
        val reloadHomeEvent = Event<Boolean>()

        /** Fires when the library has to be rebuilt, e.g. after switching accounts. */
        val reloadLibraryEvent = Event<Boolean>()

        /** Fires when the navigation header's account picture has to be redrawn. */
        val reloadAccountEvent = Event<Boolean>()
    }
}
