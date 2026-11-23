package pl.tuneprint.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pl.tuneprint.net.ImportTrack
import pl.tuneprint.net.isApplePreviewUrl
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer

internal data class Recording(val key: String, val title: String, val artist: String, val previewUrl: String)

internal class AppleCatalog(private val search: suspend (ImportTrack) -> Recording? = ::searchApple) {
    private data class Cached(val result: Recording?, val expires: Long)
    private val cache = LinkedHashMap<Pair<String, List<String>>, Cached>(128, 0.75f, true)
    private val stripes = Array(64) { Mutex() }
    private val batches = Semaphore(16)
    private val connections = Semaphore(4)
    private val requests = ArrayDeque<Long>()
    private val idPattern = Regex("[A-Za-z0-9]{22}")

    suspend fun resolve(tracks: List<ImportTrack>): Map<String, Recording> {
        require(tracks.size in 1..100 && tracks.all { track ->
            track.id.matches(idPattern) && track.title.validText() && track.artists.size in 1..32 &&
                track.artists.all { it.validText() } && track.artists.joinToString(" / ").length <= 200
        }) { "Niepoprawna lista utworów Spotify (maksymalnie 100)." }
        require(batches.tryAcquire()) { "Serwer przetwarza inne importy. Spróbuj ponownie za chwilę." }
        try {
            val result = linkedMapOf<String, Recording>()
            for (track in tracks.take(50).distinctBy { it.id }) {
                val key = track.title to track.artists
                val value = stripes[Math.floorMod(key.hashCode(), stripes.size)].withLock {
                    val cached = synchronized(cache) { cache[key] }
                    if (cached != null && cached.expires > now()) return@withLock cached.result
                    val recording = connections.withPermit {
                        awaitSearchSlot()
                        search(track)
                    }
                    synchronized(cache) {
                        cache[key] = Cached(recording, now() + if (recording == null) 600_000 else 3_600_000)
                        if (cache.size > 2048) cache.remove(cache.keys.first())
                    }
                    recording
                }
                if (value != null) result[track.id] = value
            }
            return result
        } finally {
            batches.release()
        }
    }

    private suspend fun awaitSearchSlot() {
        while (true) {
            val wait = synchronized(requests) {
                val time = now()
                while (requests.isNotEmpty() && requests.first() + 60_000 <= time) requests.removeFirst()
                if (requests.size < 20) {
                    requests.addLast(time)
                    0L
                } else requests.first() + 60_000 - time
            }
            if (wait == 0L) return
            delay(wait)
        }
    }

    private fun now() = System.nanoTime() / 1_000_000
    private fun String.validText() = isNotBlank() && length <= 200 && none(Char::isISOControl)
}

private suspend fun searchApple(track: ImportTrack): Recording? = withContext(Dispatchers.IO) {
    val term = URLEncoder.encode("${track.title} ${track.artists.first()}", Charsets.UTF_8)
    val url = URI("https://itunes.apple.com/search?country=PL&media=music&entity=song&limit=15&term=$term")
    val connection = url.toURL().openConnection() as HttpURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 5_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/json")
        if (connection.responseCode != 200) throw IOException("Apple search unavailable")
        val bytes = connection.inputStream.use { it.readNBytes(512 * 1024 + 1) }
        if (bytes.size > 512 * 1024) throw IOException("Unexpected search response")
        matchApple(track, bytes.toString(Charsets.UTF_8))
    } finally {
        connection.disconnect()
    }
}

private val separators = Regex("[^\\p{L}\\p{N}]+")
private val creditsSeparator = Regex(",\\s+|;\\s+|\\s+(?:&|and|x|feat\\.|ft\\.)\\s+", RegexOption.IGNORE_CASE)
private fun normalize(value: String) =
    Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase().replace(separators, " ").trim()

internal fun matchApple(track: ImportTrack, json: String): Recording? {
    val rows = (Json.parseToJsonElement(json) as? JsonObject)?.get("results") as? JsonArray
        ?: throw IOException("Invalid Apple search response")
    val expected = track.artists.map(::normalize)
    val title = normalize(track.title)
    for (row in rows.filterIsInstance<JsonObject>()) {
        fun field(name: String) = (row[name] as? JsonPrimitive)?.content.orEmpty()
        if (field("kind") != "song" || normalize(field("trackName")) != title) continue
        val artist = field("artistName")
        if (listOf(normalize(artist)) != expected && artist.split(creditsSeparator).map(::normalize).toSet() != expected.toSet()) continue
        val url = field("previewUrl")
        val id = field("trackId").toLongOrNull() ?: continue
        if (id > 0 && isApplePreviewUrl(url)) return Recording("apple-$id", track.title, track.artists.joinToString(" / "), url)
    }
    return null
}
