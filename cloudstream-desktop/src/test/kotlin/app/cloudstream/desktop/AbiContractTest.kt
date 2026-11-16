package app.cloudstream.desktop

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.Qualities
import android.content.Context
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The extension ABI, asserted member by member.
 *
 * Extensions ship Dalvik bytecode compiled against upstream's *Android* build. It links here
 * only because `:cs-compat` compiles upstream's `library` sources — plus the app-side `Plugin`
 * base class — so the same fully-qualified names and signatures exist on the JVM. That promise
 * is held by code someone else owns: an upstream release can rename or move a member that
 * extensions call, and everything here still compiles while every extension that touches that
 * member dies with `NoSuchMethodError` on the user's machine.
 *
 * This is that promise, written down, and it is offline on purpose: the scheduled upstream sync
 * runs `test` before it opens its pull request, so a rename in a new upstream release fails that
 * pull request instead of reaching users. When upstream changes a member on purpose, update this
 * list in the same commit that adapts the loader — a red test here is the prompt to check whether
 * extensions can still link.
 *
 * [AbiSmoke] is the other half: it loads a real published archive. That one needs the network,
 * so it lives in the scheduled live-checks workflow rather than in every pull request.
 */
class AbiContractTest {

    private fun type(name: String): Class<*> = try {
        Class.forName(name)
    } catch (t: Throwable) {
        throw AssertionError("$name is missing from the extension ABI", t)
    }

    /** Public methods, inherited ones included, because that is what bytecode links against. */
    private fun requireMethods(target: Class<*>, vararg names: String) {
        val present = target.methods.map { it.name }.toSet()
        val missing = names.filterNot { it in present }
        assertTrue(
            missing.isEmpty(),
            "${target.name} no longer declares ${missing.joinToString()}: " +
                "extensions compiled against the Android library will not link",
        )
    }

    @Test
    fun `provider base class keeps the members extensions override`() {
        val mainApi = type("com.lagradost.cloudstream3.MainAPI")
        requireMethods(
            mainApi,
            "getMainPage",
            "search",
            "quickSearch",
            "load",
            "loadLinks",
            // `open var name` / `open var mainUrl`: every provider overrides both.
            "getName",
            "getMainUrl",
        )
    }

    @Test
    fun `plugins still register providers and extractors`() {
        val base = type("com.lagradost.cloudstream3.plugins.BasePlugin")
        requireMethods(base, "registerMainAPI", "registerExtractorAPI")

        val plugin = type("com.lagradost.cloudstream3.plugins.Plugin")
        assertTrue(
            BasePlugin::class.java.isAssignableFrom(plugin),
            "Plugin no longer extends BasePlugin (${plugin.superclass?.name})",
        )

        val context = type("android.content.Context")
        assertTrue(
            plugin.methods.any { method ->
                method.name == "load" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == context
            },
            "Plugin.load(Context) is gone: archives that override it would load, register " +
                "nothing, and look installed-but-broken",
        )
    }

    @Test
    fun `the provider registry keeps its shape`() {
        val holder = type("com.lagradost.cloudstream3.APIHolder")
        requireMethods(holder, "addPluginMapping", "removePluginMapping", "initAll", "getAllProviders")
        assertTrue(
            holder.fields.any { it.name == "INSTANCE" },
            "APIHolder is no longer a Kotlin object",
        )
    }

    @Test
    fun `extractor plumbing extensions call is still there`() {
        val extractorApi = type("com.lagradost.cloudstream3.utils.ExtractorApi")
        assertTrue(
            java.lang.reflect.Modifier.isAbstract(extractorApi.modifiers),
            "ExtractorApi is no longer abstract: extractors subclass it",
        )
        // Sanity check that the reflected class is the one this test is compiled against.
        assertTrue(extractorApi == ExtractorApi::class.java, "ExtractorApi resolved to another class")

        // getExtractorApiFromName is a top-level function, so the JVM sees it as a static on
        // the file class. Extensions use it to hand a link to the extractor registry.
        val helpers = type("com.lagradost.cloudstream3.utils.ExtractorApiKt")
        requireMethods(helpers, "getExtractorApiFromName")
    }

    @Test
    fun `the response types extensions construct are present`() {
        // Types named in extension source. Nothing is instantiated here: the point is that the
        // class and its public surface still exist for the translated bytecode to reference.
        type(HomePageResponse::class.java.name)
        type(ExtractorLink::class.java.name)
        type(SubtitleFile::class.java.name)
        type(Qualities::class.java.name)
        type(MainAPI::class.java.name)
        type(APIHolder::class.java.name)
        type(Context::class.java.name)
    }
}
