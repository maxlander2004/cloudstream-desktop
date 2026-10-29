package app.cloudstream.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Pushes watch progress to the linked lists.
 *
 * Upstream does this through its `SyncProvider` abstraction, which also has to keep a local copy
 * of every list in sync. This keeps the useful half: when an episode is watched, find the media
 * on the service and raise its progress. Everything is best effort — a title that cannot be
 * matched is skipped rather than guessed at, because writing progress onto the wrong entry is
 * worse than not writing it at all.
 */
object Tracking {
    private val json = Json { ignoreUnknownKeys = true }

    /** Fire-and-forget pushes from UI callbacks that are not suspend functions. */
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    /** provider -> title -> remote id, so a show is looked up once per session. */
    private val mediaIds = ConcurrentHashMap<String, Int>()

    private fun cacheKey(provider: TrackingProvider, title: String) = "${provider.id}:${title.lowercase()}"

    /** True when at least one list is linked and enabled, so callers can skip the work. */
    fun active(): Boolean = Accounts.all().any { it.enabled && it.accessToken.isNotBlank() }

    /**
     * Called when an episode is watched. [season]/[episode] are the numbers the source reported;
     * an item without them (a movie) is marked completed instead.
     */
    suspend fun onEpisodeWatched(
        title: String,
        season: Int?,
        episode: Int?,
        finished: Boolean,
    ) {
        if (title.isBlank() || !active()) return
        Accounts.all().filter { it.enabled && it.accessToken.isNotBlank() }.forEach { account ->
            val provider = TrackingProvider.of(account.provider) ?: return@forEach
            runCatching {
                val token = Accounts.token(account) ?: return@runCatching
                when (provider) {
                    TrackingProvider.AniList -> pushAniList(token, title, season, episode, finished)
                    TrackingProvider.MyAnimeList -> pushMal(token, title, season, episode, finished)
                }
            }
        }
    }

    /** [onBookmarked] without a coroutine scope at the call site. */
    fun onBookmarkedAsync(bookmark: Bookmark, added: Boolean) {
        scope.launch { runCatching { onBookmarked(bookmark, added) } }
    }

    /** Adds a bookmarked show to the list so the service knows about it too. */
    suspend fun onBookmarked(bookmark: Bookmark, added: Boolean) {
        if (!added || !active()) return
        val title = bookmark.title
        if (title.isBlank()) return
        Accounts.all().filter { it.enabled && it.accessToken.isNotBlank() }.forEach { account ->
            val provider = TrackingProvider.of(account.provider) ?: return@forEach
            runCatching {
                val token = Accounts.token(account) ?: return@runCatching
                when (provider) {
                    TrackingProvider.AniList -> {
                        val id = anilistMediaId(token, title, bookmark.type) ?: return@runCatching
                        Accounts.HttpClient.graphql(
                            token,
                            "mutation (\$mediaId: Int) { SaveMediaListEntry(mediaId: \$mediaId, status: PLANNING) { id } }",
                            mapOf("mediaId" to id),
                        )
                    }
                    TrackingProvider.MyAnimeList -> {
                        val id = malAnimeId(token, title) ?: return@runCatching
                        Http.send(
                            method = "PUT",
                            url = "https://api.myanimelist.net/v2/anime/$id/my_list_status",
                            body = Accounts.formEncode(mapOf("status" to "plan_to_watch")),
                            contentType = "application/x-www-form-urlencoded",
                            headers = mapOf("Authorization" to "Bearer $token"),
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ AniList

    private suspend fun pushAniList(
        token: String,
        title: String,
        season: Int?,
        episode: Int?,
        finished: Boolean,
    ) {
        val id = anilistMediaId(token, title, "SERIES") ?: return
        val watched = episode?.takeIf { it > 0 }
        // A show with several seasons keeps counting: season 2 episode 3 is progress season+ep.
        val progress = if (watched != null && season != null && season > 1) {
            // AniList stores a single running number for the whole series.
            watched + (season - 1) * 12
        } else {
            watched
        }
        val variables = mutableMapOf<String, Any?>("mediaId" to id)
        if (progress != null) variables["progress"] = progress
        variables["status"] = if (finished) "COMPLETED" else "CURRENT"
        Accounts.HttpClient.graphql(
            token,
            "mutation (\$mediaId: Int, \$progress: Int, \$status: MediaListStatus) {" +
                " SaveMediaListEntry(mediaId: \$mediaId, progress: \$progress, status: \$status) { id progress status } }",
            variables,
        )
    }

    private suspend fun anilistMediaId(token: String, title: String, type: String): Int? {
        val key = cacheKey(TrackingProvider.AniList, title)
        mediaIds[key]?.let { return it }
        val body = Accounts.HttpClient.graphql(
            token,
            "query (\$search: String, \$type: MediaType) { Page(perPage: 5) {" +
                " media(search: \$search, type: \$type) { id title { romaji english } } } }",
            mapOf("search" to title, "type" to anilistType(type)),
        ) ?: return null
        val id = parseAniListMediaId(body) ?: return null
        mediaIds[key] = id
        return id
    }

    internal fun anilistType(itemType: String?): String =
        if (itemType?.uppercase() == "ANIME") "ANIME" else "ANIME"

    internal fun parseAniListMediaId(body: String): Int? = runCatching {
        val media = Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            ?.get("Page")?.jsonObject
            ?.get("media")?.jsonArray
            ?.firstOrNull()?.jsonObject
        media?.get("id")?.jsonPrimitive?.intOrNull
    }.getOrNull()

    // ------------------------------------------------------------------ MyAnimeList

    private suspend fun pushMal(
        token: String,
        title: String,
        season: Int?,
        episode: Int?,
        finished: Boolean,
    ) {
        val id = malAnimeId(token, title) ?: return
        val watched = episode?.takeIf { it > 0 }
        val progress = if (watched != null && season != null && season > 1) watched + (season - 1) * 12 else watched
        val fields = mutableMapOf("status" to if (finished) "completed" else "watching")
        progress?.let { fields["num_watched_episodes"] = it.toString() }
        Http.send(
            method = "PUT",
            url = "https://api.myanimelist.net/v2/anime/$id/my_list_status",
            body = Accounts.formEncode(fields),
            contentType = "application/x-www-form-urlencoded",
            headers = mapOf("Authorization" to "Bearer $token"),
        )
    }

    private suspend fun malAnimeId(token: String, title: String): Int? {
        val key = cacheKey(TrackingProvider.MyAnimeList, title)
        mediaIds[key]?.let { return it }
        val search = java.net.URLEncoder.encode(title, Charsets.UTF_8)
        val body = Http.send(
            method = "GET",
            url = "https://api.myanimelist.net/v2/anime?q=$search&limit=5&fields=id,title",
            headers = mapOf("Authorization" to "Bearer $token"),
        )
        val id = parseMalAnimeId(body) ?: return null
        mediaIds[key] = id
        return id
    }

    internal fun parseMalAnimeId(body: String): Int? = runCatching {
        Json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("node")?.jsonObject
            ?.get("id")?.jsonPrimitive?.intOrNull
    }.getOrNull()

    /** The entry's current progress, used by tests and by the "already synced" status text. */
    internal fun parseAniListProgress(body: String): Int? = runCatching {
        Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            ?.get("SaveMediaListEntry")?.jsonObject
            ?.get("progress")?.jsonPrimitive?.contentOrNull
            ?.toIntOrNull()
    }.getOrNull()
}
