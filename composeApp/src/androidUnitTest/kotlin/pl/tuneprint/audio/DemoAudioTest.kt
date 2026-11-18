package pl.tuneprint.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import pl.tuneprint.catalog.DemoTracks

class DemoAudioTest {
    @Test
    fun everyCatalogKeyHasLocalAudioInOfflineRoundOrder() {
        assertEquals(DemoTracks.byKey.keys.toList(), DemoAudio.byKey.keys.toList())
        assertEquals(RoundTracks.all, DemoTracks.byKey.keys.map(DemoAudio.byKey::getValue))
        val halo = DemoAudio.byKey.getValue("demo-10")
        assertEquals("Halo halo z aresztu funny.webm", halo.assetName)
        assertEquals(0, halo.startAtMs)
        assertEquals(setOf("ziobro"), halo.ownerIds)
    }
}
