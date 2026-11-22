package pl.tuneprint.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import pl.tuneprint.game.GameSession

class RoundAudioPlayerTest {
    @Test fun lateJoinAndResumeUseTheRoundClock() {
        assertEquals(7500, previewOffset(7500, 30_000))
        assertEquals(8700, previewOffset(8700, -1))
        assertEquals(0, previewOffset(-5, 30_000))
        assertNull(previewOffset(GameSession.ROUND_MS, 30_000))
        assertNull(previewOffset(GameSession.ROUND_MS + 1, 30_000))
    }

    @Test fun shortPreviewsResumeAtTheSameLoopPosition() {
        assertEquals(0, previewOffset(6000, 3000))
        assertEquals(100, previewOffset(6100, 3000))
    }
}
