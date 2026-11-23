package pl.tuneprint.server

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import pl.tuneprint.net.ImportTrack
import pl.tuneprint.net.isApplePreviewUrl
import kotlin.test.*

internal val importedTrack = ImportTrack("1234567890123456789012", "Fiji", listOf("Taco Hemingway"))
internal val recording = Recording("apple-123", "Fiji", "Taco Hemingway", "https://audio-ssl.itunes.apple.com/example.m4a")

class AppleCatalogTest {
    private fun result(title: String, artist: String, url: String = recording.previewUrl) =
        """{"kind":"song","trackId":123,"trackName":"$title","artistName":"$artist","previewUrl":"$url"}"""

    @Test fun fullTitleAndCreditsAreRequired() {
        val song = ImportTrack(importedTrack.id, "oNE", listOf("A", "B"))
        val candidates = listOf(result("One (Live)", "A & B"), result("One", "A"), result("one", "A & B", "http://example.org/x"))
        fun search(rows: List<String>) = matchApple(song, """{"results":[${rows.joinToString()}]}""")
        assertNull(search(candidates))
        val found = assertNotNull(search(candidates + result("ONE", "B & A")))
        assertEquals("apple-123", found.key)
        assertEquals(recording.previewUrl, found.previewUrl)
    }

    @Test fun onlyAppleHttpsOriginsAreAccepted() {
        assertTrue(isApplePreviewUrl("https://aod.itunes.apple.com/files/a.m4a"))
        assertTrue(isApplePreviewUrl("https://a.mzstatic.com/files/a.m4a"))
        listOf("http://a.mzstatic.com/a", "https://a.mzstatic.com.example.org/a",
            "https://user@a.mzstatic.com/a", "https://evil.test@a.mzstatic.com/a",
            "https://a.mzstatic.com:8080/a", "https://a.mzstatic.com/a#bad"
        ).forEach { assertFalse(isApplePreviewUrl(it), it) }
    }

    @Test fun concurrentImportsShareSearchesAndCacheMisses() = runBlocking {
        var count = 0
        val apple = AppleCatalog { track ->
            count++
            delay(10)
            recording.takeIf { track.title == importedTrack.title }
        }
        val missing = importedTrack.copy(id = "A".repeat(22), title = "Missing")
        val tracks = listOf(importedTrack, missing, importedTrack)
        val results = List(3) { async { apple.resolve(tracks) } }.awaitAll()
        assertTrue(results.all { it == mapOf(importedTrack.id to recording) })
        assertEquals(2, count)
        assertFailsWith<IllegalArgumentException> { apple.resolve(listOf(importedTrack.copy(id = "bad"))) }
        assertEquals(2, count)
    }

    @Test fun entireTopFiftyIsEligibleIncludingTheLastPosition() = runBlocking {
        val tracks = (1..55).map { position ->
            importedTrack.copy(id = position.toString().padStart(22, '0'),
                title = if (position <= 34) "Unavailable" else "Song $position")
        }
        val apple = AppleCatalog { track ->
            recording.copy(key = track.id).takeUnless { track.title == "Unavailable" }
        }
        assertEquals(tracks.subList(34, 50).map { it.id }, apple.resolve(tracks).keys.toList())
    }

    @Test fun retryAfterInterruptionCompletesTheRankingUsingCachedMatches() = runBlocking {
        val second = importedTrack.copy(id = "A".repeat(22), title = "Another")
        val searched = mutableListOf<String>()
        val apple = AppleCatalog {
            searched.add(it.id)
            if (searched.size == 2) throw java.io.IOException()
            recording.copy(key = it.id)
        }
        val tracks = listOf(importedTrack, second)
        assertFailsWith<java.io.IOException> { apple.resolve(tracks) }
        assertEquals(tracks.map { it.id }, apple.resolve(tracks).keys.toList())
        assertEquals(listOf(importedTrack.id, second.id, second.id), searched)
    }
}
