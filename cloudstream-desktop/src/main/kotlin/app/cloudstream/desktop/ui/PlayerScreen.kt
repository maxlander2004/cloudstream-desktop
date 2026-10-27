package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.Library
import app.cloudstream.desktop.core.Player
import app.cloudstream.desktop.core.PlaybackSession
import app.cloudstream.desktop.core.ResumePolicy
import app.cloudstream.desktop.core.Settings
import app.cloudstream.desktop.core.SubtitleOption
import app.cloudstream.desktop.core.Subtitles
import app.cloudstream.desktop.core.WatchProgress
import app.cloudstream.desktop.core.WatchRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

/**
 * The embedded playback engine. VLCJ drives the VLC/libvlc already installed on the machine,
 * so no media framework has to be shipped with the app; when VLC is missing the player screen
 * says so and the app falls back to the configured external player.
 */
object VideoEngine {
    /** libvlc discovered by VLCJ; discovery touches the filesystem, so it runs once. */
    val nativePath: String? by lazy {
        runCatching {
            val discovery = NativeDiscovery()
            if (discovery.discover()) discovery.discoveredPath() else null
        }.getOrNull()
    }

    val available: Boolean get() = nativePath != null

    fun description(): String =
        nativePath?.let { "libvlc found at $it" } ?: "VLC (libvlc) was not found on this machine"
}

/**
 * The in-app player: the original's player screen reduced to what a desktop port can do —
 * embedded video, play/pause, seeking, episode stepping, Progress → Continue watching, and
 * *Autoplay next episode*, which loads the next episode into the same player when one ends.
 */
@Composable
fun PlayerScreen(
    session: PlaybackSession,
    settings: Settings,
    onClose: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var index by remember(session) { mutableStateOf(session.startIndex) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var seekable by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(100) }
    var autoplayNext by remember { mutableStateOf(settings.autoplayNext) }
    var watchedMarked by remember(index) { mutableStateOf(false) }
    // Subtitles for the current episode: sidecar files, the source's tracks, plus any the user
    // opens by hand. A null selection means subtitles are off.
    var subtitleOptions by remember(session) { mutableStateOf<List<SubtitleOption>>(emptyList()) }
    var subtitle by remember { mutableStateOf<SubtitleOption?>(null) }
    var subtitleMenuOpen by remember { mutableStateOf(false) }
    var embeddedTracks by remember { mutableStateOf<List<TrackDescription>>(emptyList()) }

    val episode = session.episodes.getOrNull(index)
    val video: EmbeddedMediaPlayerComponent? = remember(settings.vlcPath) {
        runCatching {
            // A folder set in Settings → Player is passed to VLC as its plugin path.
            val folder = settings.vlcPath.trim()
            if (folder.isEmpty()) {
                EmbeddedMediaPlayerComponent()
            } else {
                EmbeddedMediaPlayerComponent("--plugin-path=$folder")
            }
        }.getOrNull()
    }
    val engineReady = video != null

    DisposableEffect(video) {
        onDispose { runCatching { video?.release() } }
    }

    /** The player's current episode, as the library stores it. */
    fun recordInto() {
        Library.recordWatch(
            WatchRecord(
                itemUrl = session.itemUrl,
                apiName = session.apiName,
                title = session.title,
                posterUrl = session.posterUrl,
                type = session.type,
                episodeData = episode?.data,
                episodeLabel = episode?.label.orEmpty(),
                season = episode?.season,
                episode = episode?.episode,
            ),
        )
    }

    /** Saves where playback is, so the detail screen can resume from here. */
    fun persistProgress() {
        if (durationMs <= 0) return
        Library.saveProgress(
            WatchProgress(
                itemUrl = session.itemUrl,
                episodeData = episode?.data,
                positionMs = positionMs,
                durationMs = durationMs,
            ),
        )
    }

    /**
     * End of an episode: it counts as watched, its saved position is dropped, and either the
     * next episode starts (Autoplay next episode) or playback stops.
     */
    fun finishEpisode() {
        Library.clearProgress(session.itemUrl, episode?.data)
        recordInto()
        val next = session.nextIndex(index)
        if (autoplayNext && next != null) {
            onStatus("Autoplaying ${session.episodes[next].label}")
            index = next
        } else {
            playing = false
            onStatus(if (episode != null) "Finished ${episode.label}" else "Finished ${session.title}")
        }
    }

    suspend fun startEpisode(target: Int) {
        val player = video?.mediaPlayer() ?: return
        loading = true
        error = null
        watchedMarked = false
        val links = runCatching { session.resolveStreams(target) }.getOrNull().orEmpty()
        val stream = links.firstOrNull()
        if (stream == null) {
            loading = false
            error = "No playable link was returned for this episode"
            subtitleOptions = emptyList()
            subtitle = null
            return
        }

        // Subtitles that sit next to a downloaded file come first, then the source's tracks.
        val sidecars = Subtitles.localPathOf(stream.url)
            ?.let { Subtitles.sidecarsFor(it) }
            .orEmpty()
        subtitleOptions = (sidecars + stream.subtitles.map(Subtitles::optionOf))
            .distinctBy { it.key }
        subtitle = if (settings.subtitleAutoLoad) {
            Subtitles.preferred(subtitleOptions, settings.subtitleLanguage)
        } else {
            null
        }

        val saved = Library.progressFor(session.itemUrl, session.episodes.getOrNull(target)?.data)
        val resumeMs = ResumePolicy.startPositionMs(saved, settings.resumePlayback)
        val options = buildList {
            stream.headers["Referer"]?.let { add(":http-referrer=$it") }
            stream.headers["User-Agent"]?.let { add(":http-user-agent=$it") }
            if (resumeMs > 0) add(":start-time=" + (resumeMs / 1000.0))
        }

        positionMs = resumeMs
        durationMs = saved?.durationMs ?: 0L
        if (resumeMs > 0) {
            onStatus(
                "Resuming ${session.episodes.getOrNull(target)?.label ?: session.title} " +
                    "at ${ResumePolicy.formatTime(resumeMs)}",
            )
        }
        player.media().play(stream.url, *options.toTypedArray())
    }

    LaunchedEffect(video, index) {
        if (video == null) return@LaunchedEffect
        startEpisode(index)
    }

    // VLC reports state from its own threads, so every update hops back to the UI scope.
    DisposableEffect(video, index) {
        val player = video?.mediaPlayer()
        val listener = object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                scope.launch {
                    playing = true
                    loading = false
                    error = null
                }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                scope.launch { playing = false }
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                scope.launch { durationMs = newLength.coerceAtLeast(0) }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                scope.launch { finishEpisode() }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                scope.launch {
                    error = "VLC could not play this stream"
                    loading = false
                }
            }
        }
        player?.events()?.addMediaPlayerEventListener(listener)
        onDispose { runCatching { player?.events()?.removeMediaPlayerEventListener(listener) } }
    }

    // Position polling: drives the seek bar and the saved progress.
    LaunchedEffect(video, index) {
        val player = video?.mediaPlayer() ?: return@LaunchedEffect
        var ticks = 0
        while (true) {
            delay(1000)
            val status = runCatching { player.status() }.getOrNull() ?: continue
            if (!status.isPlaying()) continue

            playing = true
            loading = false
            seekable = status.isSeekable()
            status.time().takeIf { it >= 0 }?.let { positionMs = it }
            status.length().takeIf { it > 0 }?.let { durationMs = it }
            if (durationMs <= 0) continue

            val progress = WatchProgress(
                itemUrl = session.itemUrl,
                episodeData = episode?.data,
                positionMs = positionMs,
                durationMs = durationMs,
            )
            ticks++
            if (ticks % 5 == 0) Library.saveProgress(progress)
            // Past the watched threshold the episode is marked watched, like the original.
            if (!watchedMarked && ResumePolicy.isWatched(progress)) {
                watchedMarked = true
                Library.saveProgress(progress)
                recordInto()
            }
        }
    }

    /**
     * Subtitles are attached once VLC has the media open. Switching episode clears the slaves,
     * so the effect keys on the episode index as well as on the selection.
     */
    LaunchedEffect(video, index, subtitle) {
        val player = video?.mediaPlayer() ?: return@LaunchedEffect
        val chosen = subtitle
        if (chosen == null) {
            runCatching { player.subpictures().setTrack(-1) }
            return@LaunchedEffect
        }
        // setSubTitleFile is a no-op while the media is still being opened.
        withTimeoutOrNull(15_000) { while (!playing) delay(200) }
        val file = Subtitles.download(chosen)
        if (file == null) {
            onStatus("Could not load subtitles (${chosen.label})")
            return@LaunchedEffect
        }
        val attached = runCatching { player.subpictures().setSubTitleFile(file.toFile()) }
            .getOrDefault(false)
        if (!attached) onStatus("VLC could not read the subtitle file ${file.fileName}")
    }

    // VLC only knows the media's own tracks after it has parsed the file, so refresh when the
    // menu is opened rather than when the episode starts.
    LaunchedEffect(video, index, subtitleMenuOpen) {
        if (!subtitleMenuOpen) return@LaunchedEffect
        embeddedTracks = runCatching { video?.mediaPlayer()?.subpictures()?.trackDescriptions() }
            .getOrNull()
            .orEmpty()
    }

    fun persistAndClose() {
        persistProgress()
        onClose()
    }

    /** The desktop counterpart of the original's subtitle file picker. */
    fun pickSubtitleFile(): SubtitleOption? {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Choose a subtitle file", java.awt.FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            name.substringAfterLast('.', "").lowercase() in Subtitles.extensions
        }
        dialog.isVisible = true
        val directory = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return SubtitleOption(label = name, file = java.nio.file.Paths.get(directory, name))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { persistAndClose() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close player",
                        tint = Color.White,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        session.title,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            !engineReady -> "In-app playback unavailable"
                            loading -> "Loading ${episode?.label ?: session.title}…"
                            else -> episode?.label ?: session.apiName
                        },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                loading.takeIf { it && engineReady }?.let {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                }
                session.previousIndex(index)?.let { previous ->
                    IconButton(onClick = { persistProgress(); index = previous }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous episode", tint = Color.White)
                    }
                }
                session.nextIndex(index)?.let { next ->
                    IconButton(onClick = { persistProgress(); index = next }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next episode", tint = Color.White)
                    }
                }
                if (session.isEpisodeBased) {
                    TextButton(
                        onClick = {
                            autoplayNext = !autoplayNext
                            settings.autoplayNext = autoplayNext
                            onStatus(if (autoplayNext) "Autoplay next episode on" else "Autoplay next episode off")
                        },
                    ) {
                        Text(if (autoplayNext) "Autoplay on" else "Autoplay off", color = Color.White, fontSize = 12.sp)
                    }
                }
                TextButton(
                    onClick = {
                        persistProgress()
                        scope.launch {
                            val link = runCatching { session.resolveStreams(index) }.getOrNull()
                                ?.firstOrNull()
                            onStatus(
                                if (link != null) {
                                    Player.open(link, settings.playerCommand)
                                } else {
                                    "No link to open externally"
                                },
                            )
                        }
                    },
                ) { Text("Open externally", color = Color.White, fontSize = 12.sp) }
            }

            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                if (video != null) {
                    SwingPanel(factory = { video }, modifier = Modifier.fillMaxSize())
                }

                if (!engineReady) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("In-app playback needs VLC", color = Color.White, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            VideoEngine.description() + ". Install VLC and reopen this screen, or use " +
                                "\"Open externally\" with the player command from Settings → Player.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val link = runCatching { session.resolveStreams(index) }.getOrNull()
                                        ?.firstOrNull()
                                    onStatus(
                                        if (link != null) {
                                            Player.open(link, settings.playerCommand)
                                        } else {
                                            "No link to open externally"
                                        },
                                    )
                                }
                            },
                        ) { Text("Open in external player") }
                        error?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                } else {
                    error?.let { message ->
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        val controls = video?.mediaPlayer()?.controls() ?: return@IconButton
                        if (playing) controls.pause() else controls.play()
                    },
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = Color.White,
                    )
                }
                Text(ResumePolicy.formatTime(positionMs), color = Color.White, fontSize = 12.sp)
                Slider(
                    value = positionMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()),
                    onValueChange = { value ->
                        val player = video?.mediaPlayer() ?: return@Slider
                        runCatching { player.controls().setTime(value.toLong()) }
                        positionMs = value.toLong()
                    },
                    enabled = seekable && durationMs > 0,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(ResumePolicy.formatTime(durationMs), color = Color.White, fontSize = 12.sp)
                Box {
                    IconButton(onClick = { subtitleMenuOpen = true }) {
                        Icon(
                            Icons.Outlined.Subtitles,
                            contentDescription = "Subtitles",
                            tint = if (subtitle != null) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = subtitleMenuOpen,
                        onDismissRequest = { subtitleMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Off") },
                            onClick = {
                                subtitle = null
                                runCatching { video?.mediaPlayer()?.subpictures()?.setTrack(-1) }
                                subtitleMenuOpen = false
                            },
                        )
                        subtitleOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    subtitle = option
                                    subtitleMenuOpen = false
                                },
                            )
                        }
                        embeddedTracks.filter { it.id() >= 0 }.forEach { track ->
                            DropdownMenuItem(
                                text = { Text("Embedded · ${track.description()}") },
                                onClick = {
                                    subtitle = null
                                    runCatching { video?.mediaPlayer()?.subpictures()?.setTrack(track.id()) }
                                    subtitleMenuOpen = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Open subtitle file…") },
                            onClick = {
                                subtitleMenuOpen = false
                                pickSubtitleFile()?.let { picked ->
                                    subtitleOptions = (listOf(picked) + subtitleOptions)
                                        .distinctBy { it.key }
                                    subtitle = picked
                                }
                            },
                        )
                    }
                }
                IconButton(
                    onClick = {
                        muted = !muted
                        video?.mediaPlayer()?.audio()?.setMute(muted)
                    },
                ) {
                    Icon(
                        if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (muted) "Unmute" else "Mute",
                        tint = Color.White,
                    )
                }
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { value ->
                        volume = value.toInt()
                        video?.mediaPlayer()?.audio()?.setVolume(volume)
                    },
                    valueRange = 0f..200f,
                    modifier = Modifier.width(120.dp),
                )
            }
        }
    }
}
