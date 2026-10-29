package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.ItemType
import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.Bookmark
import app.cloudstream.desktop.core.DownloadManager
import app.cloudstream.desktop.core.Library
import app.cloudstream.desktop.core.PlaybackSession
import app.cloudstream.desktop.core.Player
import app.cloudstream.desktop.core.ResumePolicy
import app.cloudstream.desktop.core.SessionEpisode
import app.cloudstream.desktop.core.Settings
import app.cloudstream.desktop.core.bestDownloadLink
import app.cloudstream.desktop.core.episodeLabelOf
import app.cloudstream.desktop.core.sessionEpisodes
import app.cloudstream.desktop.core.WatchRecord
import app.cloudstream.desktop.core.legacy.DesktopEpisode
import app.cloudstream.desktop.core.legacy.EpisodeSelection
import app.cloudstream.desktop.core.legacy.LegacyCatalog
import app.cloudstream.desktop.core.legacy.ResultItem
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.DubStatus
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.util.Locale

/**
 * The original's result page: back/share/bookmark actions, poster and metadata, cast, tags,
 * the season/dub/episode pickers with Resume and Next episode, the playable links and the
 * recommendations rail.
 */
@Composable
fun ResultScreen(
    item: ResultItem,
    catalog: Catalog,
    posterSize: Int,
    titleUnder: Boolean,
    settings: Settings,
    resumeEpisodeData: String? = null,
    onDismiss: () -> Unit,
    onPlay: (StreamLink, PlaybackSession?) -> Unit,
    onDownload: (StreamLink) -> Unit,
    onOpenRecommendation: (CatalogItem) -> Unit,
    onSearchTag: (String) -> Unit,
    onStatus: (String) -> Unit,
) {
    val legacy = catalog as? LegacyCatalog
    val scope = rememberCoroutineScope()
    var episodeMap by remember { mutableStateOf<Map<DubStatus, Map<Int, List<DesktopEpisode>>>?>(null) }
    var episodeSelection by remember { mutableStateOf<EpisodeSelection?>(null) }
    var loadingStreams by remember { mutableStateOf(false) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var streams by remember { mutableStateOf<List<StreamLink>>(emptyList()) }
    // Bumped by the Retry button so a failed resolution can be run again.
    var retryCount by remember { mutableStateOf(0) }
    var bookmarked by remember(item.url) { mutableStateOf(Library.isBookmarked(item.url)) }
    var sortDescending by remember { mutableStateOf(false) }

    /** "S1E4 · Name", the label upstream puts on an episode row and its card. */
    fun episodeLabel(episode: DesktopEpisode): String = episodeLabelOf(episode)

    fun selectEpisode(season: Int, episode: DesktopEpisode) {
        val dub = episodeMap?.entries
            ?.firstOrNull { (_, seasons) -> seasons[season]?.any { it.data == episode.data } == true }
            ?.key
            ?: episodeSelection?.dubStatus
            ?: return
        episodeSelection = EpisodeSelection(dub, season, episode)
    }

    /** Continue watching is fed from here: the episode the user actually started. */
    fun recordWatch(episode: DesktopEpisode?) {
        Library.recordWatch(
            WatchRecord(
                itemUrl = item.url,
                apiName = item.apiName,
                title = item.title,
                posterUrl = item.posterUrl,
                type = item.type.name,
                episodeData = episode?.data,
                episodeLabel = episode?.let(::episodeLabel).orEmpty(),
                season = episode?.season,
                episode = episode?.episode,
            ),
        )
    }

    LaunchedEffect(item, resumeEpisodeData) {
        if (!item.episodeBased) return@LaunchedEffect
        val loaded = legacy?.episodeMapFor(item.url)
        if (loaded.isNullOrEmpty()) {
            episodeMap = null
            return@LaunchedEffect
        }
        episodeMap = loaded
        // Continue watching jumps straight to the episode it was left on.
        val resume = resumeEpisodeData?.let { data ->
            loaded.entries.firstNotNullOfOrNull { (dub, seasons) ->
                seasons.entries.firstNotNullOfOrNull { (season, episodes) ->
                    episodes.firstOrNull { it.data == data }?.let { EpisodeSelection(dub, season, it) }
                }
            }
        }
        episodeSelection = resume ?: run {
            val firstDub = loaded.keys.first()
            val seasonsForDub = loaded[firstDub].orEmpty()
            val firstSeason = seasonsForDub.keys.firstOrNull()
            EpisodeSelection(firstDub, firstSeason, firstSeason?.let { seasonsForDub[it] }?.firstOrNull())
        }
    }

    LaunchedEffect(episodeSelection, retryCount) {
        val selection = episodeSelection
        if (selection?.canResolve != true && item.directDataUrl == null) return@LaunchedEffect
        loadingStreams = true
        streamError = null
        streams = emptyList()
        try {
            // Movies and live streams carry a dataUrl; series/anime resolve per episode.
            val resolved = when {
                legacy == null -> emptyList()
                item.directDataUrl != null -> legacy.streamsFor(item.directDataUrl)
                else -> legacy.streamsFor(selection?.episode?.data, selection?.episode?.name)
            }
            streams = resolved
            if (resolved.isEmpty()) streamError = "No streams returned for this item"
        } catch (t: Throwable) {
            streamError = t.message ?: "Could not resolve streams"
        } finally {
            loadingStreams = false
        }
    }

    /**
     * Upstream's batch download: resolve and queue one link per episode. [batchName] names the
     * run ("Season 2", "All seasons") so Downloads can show it as one job rather than N rows.
     */
    fun downloadSeason(episodes: List<DesktopEpisode>, batchName: String) {
        val target = legacy
        if (target == null) {
            onStatus("This source cannot be batch downloaded")
            return
        }
        if (episodes.isEmpty()) return
        val group = "${item.title} · $batchName"
        scope.launch {
            var queued = 0
            var skipped = 0
            // Pressing the button twice must not queue the same episode twice.
            val alreadyQueued = DownloadManager.tasks.value.map { task -> task.url }.toSet()
            episodes.forEachIndexed { index, episode ->
                onStatus("Downloading ${index + 1}/${episodes.size}…")
                val links = runCatching { target.streamsFor(episode.data, episode.name) }
                    .getOrNull()
                    .orEmpty()
                // A direct file wins over an HLS manifest: a saved playlist is not a video.
                val best = links.bestDownloadLink()
                if (best == null || best.url in alreadyQueued) {
                    skipped++
                    return@forEachIndexed
                }
                DownloadManager.add(
                    settings = settings,
                    title = "${item.title} · ${episodeLabel(episode)}",
                    url = best.url,
                    headers = best.headers,
                    subtitles = best.subtitles,
                    group = group,
                )
                queued++
            }
            onStatus(
                when {
                    queued > 0 && skipped > 0 ->
                        "Queued $queued episode(s); $skipped already queued or had no link"
                    queued > 0 -> "Queued $queued episode(s) for $batchName — see Downloads"
                    else -> "Nothing new to download in $batchName"
                },
            )
        }
    }

    /**
     * The playback session for the in-app player: the chosen link plus every episode in order,
     * so *Autoplay next episode* knows where to continue.
     */
    fun playbackSession(stream: StreamLink): PlaybackSession? {
        val selection = episodeSelection
        val episodes = if (item.episodeBased && episodeMap != null) {
            sessionEpisodes(episodeMap!![selection?.dubStatus ?: DubStatus.None] ?: emptyMap())
                .map { episode ->
                    if (episode.data == selection?.episode?.data) {
                        episode.copy(streams = streams)
                    } else {
                        episode
                    }
                }
        } else {
            // Movies and live streams play one resolved link.
            listOf(
                SessionEpisode(
                    label = item.title,
                    data = item.directDataUrl,
                    season = null,
                    episode = null,
                    streams = listOf(stream),
                ),
            )
        }
        if (episodes.isEmpty()) return null
        val start = episodes.indexOfFirst { episode ->
            if (item.episodeBased) episode.data == selection?.episode?.data else true
        }.coerceAtLeast(0)
        return PlaybackSession(
            itemUrl = item.url,
            title = item.title,
            apiName = item.apiName,
            posterUrl = item.posterUrl,
            type = item.type.name,
            episodes = episodes,
            startIndex = start,
            directDataUrl = item.directDataUrl,
            catalog = legacy,
        )
    }

    // Upstream opens the result as a full page, not a sheet.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.score?.takeIf { it > 0f }?.let { score ->
                    Text(
                        String.format(Locale.US, "%.1f", score),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        bookmarked = Library.toggleBookmark(
                            Bookmark(
                                url = item.url,
                                apiName = item.apiName,
                                title = item.title,
                                posterUrl = item.posterUrl,
                                type = item.type.name,
                            ),
                        )
                        onStatus(if (bookmarked) "Bookmarked ${item.title}" else "Removed bookmark")
                    },
                ) {
                    Icon(
                        if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark",
                        tint = if (bookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    onClick = {
                        runCatching {
                            Toolkit.getDefaultToolkit().systemClipboard
                                .setContents(StringSelection(item.url), null)
                        }
                        onStatus("Link copied to the clipboard")
                    },
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share")
                }
                if (item.url.startsWith("http")) {
                    IconButton(
                        onClick = {
                            runCatching { Desktop.getDesktop().browse(URI.create(item.url)) }
                            onStatus("Opened in your browser")
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open in browser")
                    }
                }
                item.trailers.firstOrNull()?.let { trailer ->
                    TextButton(
                        onClick = {
                            onStatus(Player.open(StreamLink("Trailer", trailer), settings.playerCommand))
                        },
                    ) { Text("Trailer", fontSize = 12.sp) }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val poster = item.posterUrl
                val posterWidth = when {
                    posterSize <= 1 -> 60.dp
                    posterSize >= 5 -> 120.dp
                    else -> (80 + posterSize * 10).dp
                }
                if (poster != null) {
                    AsyncImage(
                        model = poster,
                        contentDescription = item.title,
                        modifier = Modifier
                            .width(posterWidth)
                            .height(posterWidth * 1.5f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.apiName,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            item.type.displayName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        item.year?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                it.toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        item.showStatus?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    item.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            plot,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    item.durationMinutes?.takeIf { it > 0 }?.let { minutes ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$minutes min",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Upstream's cast rail, under the description.
            if (item.cast.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Cast",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(item.cast) { member ->
                        Column(
                            modifier = Modifier.width(88.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface),
                            ) {
                                if (member.imageUrl != null) {
                                    AsyncImage(
                                        model = member.imageUrl,
                                        contentDescription = member.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                member.name,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 11.sp,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                            )
                            member.role?.let { role ->
                                Text(
                                    role,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            if (item.tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(item.tags) { tag ->
                        // A tag opens the search for it, so the chips lead somewhere.
                        FilterChip(
                            selected = false,
                            onClick = { onSearchTag(tag) },
                            label = { Text(tag, fontSize = 11.sp) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (item.episodeBased && episodeMap != null) {
                EpisodeSelector(
                    item = item,
                    episodeMap = episodeMap!!,
                    selection = episodeSelection!!,
                    showDubbedSubbed = settings.displayDubbedSubbed,
                    sortDescending = sortDescending,
                    onToggleSort = { sortDescending = !sortDescending },
                    onDownloadSeason = { episodes, scope -> downloadSeason(episodes, scope) },
                    onDubStatusChange = { newDub ->
                        val seasonsForDub = episodeMap!![newDub] ?: emptyMap()
                        val firstSeason = seasonsForDub.keys.firstOrNull()
                        val episodesForSeason = seasonsForSeason(firstSeason, seasonsForDub)
                        episodeSelection = EpisodeSelection(
                            newDub,
                            firstSeason,
                            episodesForSeason.firstOrNull(),
                        )
                    },
                    onSeasonChange = { newSeason ->
                        val episodesForSeason = seasonsForSeason(
                            newSeason,
                            episodeMap!![episodeSelection!!.dubStatus] ?: emptyMap(),
                        )
                        episodeSelection = episodeSelection!!.copy(
                            season = newSeason,
                            episode = episodesForSeason.firstOrNull(),
                        )
                    },
                    onEpisodeChange = { episode ->
                        episodeSelection = episodeSelection!!.copy(episode = episode)
                    },
                )

                // Upstream's Resume / Next episode buttons, from the local watch history.
                val allEpisodes = episodeMap!!.values.flatMap { seasons ->
                    seasons.entries.flatMap { (season, list) -> list.map { season to it } }
                }
                val lastWatched = Library.lastWatched(item.url)
                    ?.episodeData
                    ?.let { data -> allEpisodes.firstOrNull { it.second.data == data } }
                val nextEpisode = lastWatched?.let { (season, episode) ->
                    allEpisodes.firstOrNull {
                        it.first == season && (it.second.episode ?: 0) > (episode.episode ?: 0)
                    }
                }
                if (lastWatched != null) {
                    val resumeFrom = Library.progressFor(item.url, lastWatched.second.data)
                        ?.let { ResumePolicy.startPositionMs(it, settings.resumePlayback) }
                        ?: 0L
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { selectEpisode(lastWatched.first, lastWatched.second) }) {
                            Text(
                                if (resumeFrom > 0) {
                                    "Resume ${episodeLabel(lastWatched.second)} · " +
                                        ResumePolicy.formatTime(resumeFrom)
                                } else {
                                    "Resume ${episodeLabel(lastWatched.second)}"
                                },
                                fontSize = 12.sp,
                            )
                        }
                        if (nextEpisode != null) {
                            OutlinedButton(onClick = { selectEpisode(nextEpisode.first, nextEpisode.second) }) {
                                Text("Next ${episodeLabel(nextEpisode.second)}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                MetadataRow(
                    item = item,
                    duration = if (item.isLive) null else item.durationMinutes,
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            StreamList(
                streams = streams,
                loading = loadingStreams,
                error = streamError,
                onRetry = { retryCount++ },
                onPlay = { stream ->
                    recordWatch(episodeSelection?.episode)
                    onPlay(stream, playbackSession(stream))
                    onDismiss()
                },
                onDownload = { stream ->
                    recordWatch(episodeSelection?.episode)
                    onDownload(stream)
                    onDismiss()
                },
            )

            if (item.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                RailHeader("Recommendations")
                Spacer(Modifier.height(6.dp))
                PosterRailRow(
                    items = item.recommendations,
                    titleUnder = titleUnder,
                    onOpen = onOpenRecommendation,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Upstream's media-type labels, as the result screen's meta row shows them. */
internal val ItemType.displayName: String
    get() = when (this) {
        ItemType.MOVIE -> "Movie"
        ItemType.SERIES -> "Series"
        ItemType.ANIME -> "Anime"
        ItemType.LIVE -> "Live"
        ItemType.NSFW -> "NSFW"
        ItemType.OTHER -> "Other"
    }

private fun seasonsForSeason(
    season: Int?,
    seasonsForDub: Map<Int, List<DesktopEpisode>>,
): List<DesktopEpisode> {
    return season?.let { seasonsForDub[it] }.orEmpty()
}

@Composable
private fun EpisodeSelector(
    item: ResultItem,
    episodeMap: Map<DubStatus, Map<Int, List<DesktopEpisode>>>,
    selection: EpisodeSelection,
    showDubbedSubbed: Boolean,
    sortDescending: Boolean,
    onToggleSort: () -> Unit,
    onDownloadSeason: (List<DesktopEpisode>, String) -> Unit,
    onDubStatusChange: (DubStatus) -> Unit,
    onSeasonChange: (Int) -> Unit,
    onEpisodeChange: (DesktopEpisode) -> Unit,
) {
    val dubLabels = episodeMap.keys.map { status ->
        status to when (status) {
            DubStatus.Dubbed -> "Dubbed"
            DubStatus.Subbed -> "Subbed"
            else -> "Unknown"
        }
    }
    val allSeasons = episodeMap.values.flatMap { it.keys }.sorted().distinct()

    val episodes = seasonsForSeason(selection.season, episodeMap[selection.dubStatus] ?: emptyMap())
        .sortedBy { it.episode ?: 0 }
        .let { if (sortDescending) it.reversed() else it }

    Column {
        // Providers → Show dubbed / subbed hides the duplicate versions; the current one stays.
        if (showDubbedSubbed) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dubLabels.forEach { (status, label) ->
                    FilterChip(
                        selected = selection.dubStatus == status,
                        onClick = { onDubStatusChange(status) },
                        label = { Text(label, fontSize = 12.sp) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allSeasons.forEach { season ->
                val label = item.seasonNames.firstOrNull { it.first == season }?.second
                    ?: "Season $season"
                FilterChip(
                    selected = selection.season == season,
                    onClick = { onSeasonChange(season) },
                    label = { Text(label, fontSize = 12.sp) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // The season's own name, so a queued batch reads "Season 2" not "Season null".
        val seasonName = item.seasonNames.firstOrNull { it.first == selection.season }?.second
            ?: selection.season?.let { season -> "Season $season" }
            ?: "This season"

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "${episodes.size} episode(s)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleSort) {
                Text(if (sortDescending) "Newest first" else "Oldest first", fontSize = 12.sp)
            }
            // Upstream's `result_batch_download_button`: queue the whole season at once.
            Button(
                onClick = { onDownloadSeason(episodes, seasonName) },
                enabled = episodes.isNotEmpty(),
            ) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Download all", fontSize = 12.sp)
            }
            // Every season at once. Still limited to the dub status being viewed, so a show
            // that ships both dubs does not queue the same episodes twice.
            val everySeason = episodeMap[selection.dubStatus].orEmpty()
                .toSortedMap()
                .values
                .flatMap { list -> list.sortedBy { it.episode ?: 0 } }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = { onDownloadSeason(everySeason, "All seasons") },
                enabled = everySeason.isNotEmpty(),
            ) {
                Text("All seasons (${everySeason.size})", fontSize = 12.sp)
            }
        }

        if (episodes.isEmpty()) {
            Text(
                "No episodes for this season",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
            ) {
                items(episodes, key = { it.upstream.data }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        selected = episode == selection.episode,
                        watched = Library.isEpisodeWatched(episode.data),
                        onClick = { onEpisodeChange(episode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: DesktopEpisode,
    selected: Boolean,
    watched: Boolean,
    onClick: () -> Unit,
) {
    val episodeNum = episode.episode ?: return
    val name = episode.name
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (watched) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Watched",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (name != null) "$episodeNum · $name" else "Episode $episodeNum",
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            episode.runTimeMinutes?.let { rt ->
                Text(
                    text = "${rt}m",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.width(40.dp),
                )
            }
            episode.score?.takeIf { it > 0f }?.let { sc ->
                Text(
                    text = String.format(Locale.US, "%.1f", sc),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    modifier = Modifier.width(28.dp),
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp),
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(
    item: ResultItem,
    duration: Int?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = item.type.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (duration != null && duration > 0) {
            Text(
                text = String.format(Locale.US, "%d min", duration),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun StreamList(
    streams: List<StreamLink>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPlay: (StreamLink) -> Unit,
    onDownload: (StreamLink) -> Unit,
) {
    when {
        loading -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp).width(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Resolving streams…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        error != null -> {
            Text(text = error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
        else -> {
            if (streams.isEmpty()) {
                Text(
                    "No streams returned for this item",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                ) {
                    items(streams) { stream ->
                        StreamRow(
                            stream = stream,
                            onPlay = { onPlay(stream) },
                            onDownload = { onDownload(stream) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamRow(
    stream: StreamLink,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stream.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            val provider = stream.headers["Referer"]
                ?: stream.headers["Origin"]
                ?: stream.headers["User-Agent"]
                ?: stream.url.substringBefore("/")
            Text(
                provider,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        Button(onClick = onPlay) {
            Text(text = "Play", fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onDownload) {
            Text("Download")
        }
    }
}
