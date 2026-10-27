package app.cloudstream.desktop

import app.cloudstream.desktop.ui.VideoEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The embedded player has to degrade to the external player when VLC is missing, so engine
 * discovery must answer instead of throwing. This machine may or may not have VLC installed.
 */
class VideoEngineTest {
    @Test
    fun `discovery answers without throwing and reports a status`() {
        val path = VideoEngine.nativePath

        assertEquals(path != null, VideoEngine.available)
        assertFalse(VideoEngine.description().isBlank())
    }
}
