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
    @Test fun searchFindsSongIdsForMatchingTitles() {
        fun row(title: String, kind: String, id: Long) =
            """{"title":"$title","contentDescriptor":{"kind":"$kind","identifiers":{"storeAdamID":"$id"}}}"""
        val rows = listOf(row("Panic", "song", 123), row("Panic", "album", 456), row("Other", "song", 789))
        val page = """<script id="serialized-server-data">[{} ,{"items":[${rows.joinToString()}]}]</script>"""
        assertEquals(setOf(123L), appleSongIds(page, "panic"))
    }

    @Test fun previewRequiresMatchingSongAndArtists() {
        fun page(title: String, artist: String) = """<script type="application/ld+json">
            {"audio":{"@type":"MusicRecording","name":"$title","byArtist":[{"name":"$artist"}],
            "audio":{"@type":"AudioObject","contentUrl":"${recording.previewUrl}"}}}</script>"""
        assertNull(matchApple(importedTrack, 123, page("Fiji (Live)", recording.artist)))
        assertNull(matchApple(importedTrack, 123, page("Fiji", "Other")))
        assertEquals(recording, matchApple(importedTrack, 123, page("Fiji", recording.artist)))
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
