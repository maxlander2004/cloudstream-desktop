package app.cloudstream.desktop

import app.cloudstream.desktop.core.BatchProgress
import app.cloudstream.desktop.core.DownloadState
import app.cloudstream.desktop.core.DownloadTask
import app.cloudstream.desktop.core.failedInGroup
import app.cloudstream.desktop.ui.DownloadsRow
import app.cloudstream.desktop.ui.queueRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A batch (a season, or every season) shown as one job on the Downloads screen. */
class DownloadBatchTest {
    private fun task(
        id: String,
        group: String? = null,
        state: DownloadState = DownloadState.QUEUED,
        total: Long = -1,
        done: Long = 0,
    ) = DownloadTask(
        id = id,
        title = id,
        url = "https://cdn.example/$id.mp4",
        fileName = "$id.mp4",
        state = state,
        totalBytes = total,
        doneBytes = done,
        group = group,
    )

    @Test
    fun `a batch folds into one row where its first episode was queued`() {
        val rows = queueRows(
            listOf(
                task("a"),
                task("b", group = "Show · All seasons"),
                task("c", group = "Show · All seasons"),
                task("d"),
            ),
        )

        assertEquals(listOf("a", "group:Show · All seasons", "d"), rows.map { it.key })
        val batch = rows[1] as DownloadsRow.Batch
        assertEquals(listOf("b", "c"), batch.tasks.map { it.id })
    }

    @Test
    fun `separate batches stay separate rows`() {
        val rows = queueRows(
            listOf(
                task("a", group = "Show · Season 1"),
                task("b", group = "Show · Season 2"),
            ),
        )

        assertEquals(2, rows.size)
        assertTrue(rows.all { it is DownloadsRow.Batch })
    }

    @Test
    fun `a batch is settled once nothing is queued or running`() {
        val progress = BatchProgress.of(
            listOf(
                task("1", "Show", DownloadState.DONE, total = 100, done = 100),
                task("2", "Show", DownloadState.FAILED),
            ),
        )

        assertEquals(2, progress.total)
        assertEquals(1, progress.done)
        assertEquals(1, progress.failed)
        assertTrue(progress.settled)
        assertEquals(1f, progress.fraction, 0.001f)
    }

    @Test
    fun `fraction counts finished episodes while sizes are still unknown`() {
        val progress = BatchProgress.of(
            listOf(
                task("1", "Show", DownloadState.DONE, total = 100, done = 100),
                task("2", "Show", DownloadState.RUNNING, total = 100, done = 50),
                task("3", "Show"),
            ),
        )

        assertFalse(progress.settled)
        assertEquals(2, progress.queued + progress.running)
        assertEquals(1f / 3f, progress.fraction, 0.001f)
    }

    @Test
    fun `fraction uses bytes once every episode has reported a size`() {
        val progress = BatchProgress.of(
            listOf(
                task("1", "Show", DownloadState.DONE, total = 100, done = 100),
                task("2", "Show", DownloadState.RUNNING, total = 100, done = 50),
            ),
        )

        assertEquals(0, progress.sizesUnknown)
        assertEquals(0.75f, progress.fraction, 0.001f)
    }

    @Test
    fun `only the failed episodes of the batch are retried`() {
        val tasks = listOf(
            task("1", "Show", DownloadState.DONE),
            task("2", "Show", DownloadState.FAILED),
            task("3", "Show", DownloadState.QUEUED),
            task("4", "Other show", DownloadState.FAILED),
        )

        assertEquals(listOf("2"), failedInGroup(tasks, "Show").map { it.id })
    }

    @Test
    fun `a cancelled episode is retryable like any other failure`() {
        val cancelled = task("1", "Show").copy(state = DownloadState.FAILED, error = "Cancelled")

        assertEquals(listOf("1"), failedInGroup(listOf(cancelled), "Show").map { it.id })
    }
}
