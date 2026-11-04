package app.cloudstream.desktop

import android.content.Context
import app.cloudstream.desktop.core.legacy.LegacyExtensions
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

private class StubProvider(private val id: String) : MainAPI() {
    override var name: String = id
    override var mainUrl: String = "https://$id.example"
}

/** The current plugin API: only `load(context)` is overridden. */
private class ContextPlugin : Plugin() {
    var received: Context? = null
    var registered: StubProvider? = null

    override fun load(context: Context) {
        received = context
        registered = StubProvider("context").also { registerMainAPI(it) }
    }
}

/** The older plugin API: only the cross-platform `load()` is overridden. */
private class LegacyPlugin : Plugin() {
    var registered: StubProvider? = null

    override fun load() {
        registered = StubProvider("legacy").also { registerMainAPI(it) }
    }
}

/**
 * Extensions built against the current plugin API override `load(context)`; calling the plain
 * `load()` on those registered no providers, which is why most community extensions installed
 * but never appeared as sources.
 */
class LegacyPluginLoadTest {
    @Test
    fun `load(context) plugins register their providers`() {
        val plugin = ContextPlugin()
        val before = APIHolder.allProviders.size
        try {
            LegacyExtensions.loadPlugin(plugin, Context())
            assertNotNull(plugin.received, "load(context) was not invoked")
            assertEquals(before + 1, APIHolder.allProviders.size)
        } finally {
            plugin.registered?.let { APIHolder.allProviders.remove(it) }
        }
    }

    @Test
    fun `load() plugins still run through the context overload`() {
        val plugin = LegacyPlugin()
        val before = APIHolder.allProviders.size
        try {
            LegacyExtensions.loadPlugin(plugin, Context())
            assertNotNull(plugin.registered, "the load() fallback did not run")
            assertEquals(before + 1, APIHolder.allProviders.size)
        } finally {
            plugin.registered?.let { APIHolder.allProviders.remove(it) }
        }
    }
}
