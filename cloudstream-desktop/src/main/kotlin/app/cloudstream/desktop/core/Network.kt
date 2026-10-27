package app.cloudstream.desktop.core

/**
 * The URL rewrites behind General → Network & Proxy.
 *
 * Both toggles exist in the Android app's bypass settings; here they are applied to every
 * request the app makes itself ([Http], which the repository, update check, playlist and
 * download paths all go through).
 */
object Network {

    /** Applies the GitHub proxy and the clone-site rules to [url], in that order. */
    fun rewrite(
        url: String,
        cloneSite: String = "",
        githubProxy: Boolean = false,
    ): String {
        val proxied = if (githubProxy) viaJsDelivr(url) else url
        return applyCloneSites(proxied, cloneSite)
    }

    /**
     * `raw.githubusercontent.com/owner/repo/branch/path` → `cdn.jsdelivr.net/gh/owner/repo@branch/path`.
     *
     * jsDelivr mirrors GitHub repositories, so this is the escape hatch for networks where
     * raw.githubusercontent.com is blocked — the same trick upstream's setting uses.
     */
    internal fun viaJsDelivr(url: String): String {
        val prefix = "https://raw.githubusercontent.com/"
        if (!url.startsWith(prefix, ignoreCase = true)) return url
        val parts = url.substring(prefix.length).split('/', limit = 4)
        if (parts.size < 4) return url
        val (owner, repo, branch, path) = parts
        if (owner.isEmpty() || repo.isEmpty() || branch.isEmpty() || path.isEmpty()) return url
        return "https://cdn.jsdelivr.net/gh/$owner/$repo@$branch/$path"
    }

    /**
     * `originalUrl=replacementUrl` entries, separated by newlines or semicolons. Entries are
     * matched by prefix, longest first, so a specific rule can override a broader one.
     * Anything that is not a `from=to` pair is ignored rather than failing the request.
     */
    internal fun applyCloneSites(url: String, cloneSite: String): String {
        if (cloneSite.isBlank()) return url
        val rules = cloneSite.split('\n', ';')
            .mapNotNull { entry ->
                val trimmed = entry.trim()
                val separator = trimmed.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val from = trimmed.substring(0, separator).trim().trimEnd('/')
                val to = trimmed.substring(separator + 1).trim().trimEnd('/')
                if (from.isEmpty() || to.isEmpty()) null else from to to
            }
            .sortedByDescending { it.first.length }

        val match = rules.firstOrNull { url.startsWith(it.first, ignoreCase = true) } ?: return url
        return match.second + url.substring(match.first.length)
    }
}
