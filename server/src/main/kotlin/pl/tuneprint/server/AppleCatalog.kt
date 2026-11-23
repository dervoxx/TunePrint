package pl.tuneprint.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pl.tuneprint.net.CatalogProgress
import pl.tuneprint.net.ImportTrack
import pl.tuneprint.net.isApplePreviewUrl
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer

internal data class Recording(val key: String, val title: String, val artist: String, val previewUrl: String)

internal class AppleCatalog(private val search: (suspend (ImportTrack) -> Recording?)? = null) {
    private data class Cached(val result: Recording?, val expires: Long)
    private val cache = LinkedHashMap<Pair<String, List<String>>, Cached>(128, 0.75f, true)
    private val stripes = Array(64) { Mutex() }
    private val batches = Semaphore(16)
    private val connections = Semaphore(4)
    private val idPattern = Regex("[A-Za-z0-9]{22}")

    suspend fun resolve(tracks: List<ImportTrack>, onProgress: suspend (CatalogProgress) -> Unit = {}): Map<String, Recording> {
        require(tracks.size in 1..100 && tracks.all { track ->
            track.id.matches(idPattern) && track.title.validText() && track.artists.size in 1..32 &&
                track.artists.all { it.validText() } && track.artists.joinToString(" / ").length <= 200
        }) { "Niepoprawna lista utworów Spotify (maksymalnie 100)." }
        require(batches.tryAcquire()) { "Serwer przetwarza inne importy. Spróbuj ponownie za chwilę." }
        try {
            val ranking = tracks.take(50).distinctBy { it.id }
            val progressLock = Mutex()
            var checked = 0
            var found = 0
            return coroutineScope {
                ranking.map { track -> async {
                    val recording = resolveTrack(track)
                    progressLock.withLock {
                        checked++
                        if (recording != null) found++
                        onProgress(CatalogProgress(checked, ranking.size, found))
                    }
                    recording?.let { track.id to it }
                } }.awaitAll().filterNotNull().toMap()
            }
        } finally {
            batches.release()
        }
    }

    private suspend fun resolveTrack(track: ImportTrack): Recording? {
        val key = track.title to track.artists
        return stripes[Math.floorMod(key.hashCode(), stripes.size)].withLock {
            val cached = synchronized(cache) { cache[key] }
            if (cached != null && cached.expires > now()) return@withLock cached.result
            val recording = connections.withPermit {
                if (search == null) searchApple(track) else search(track)
            }
            synchronized(cache) {
                cache[key] = Cached(recording, now() + if (recording == null) 600_000 else 3_600_000)
                if (cache.size > 2048) cache.remove(cache.keys.first())
            }
            recording
        }
    }

    private fun now() = System.nanoTime() / 1_000_000
    private fun String.validText() = isNotBlank() && length <= 200 && none(Char::isISOControl)
}

private suspend fun searchApple(track: ImportTrack): Recording? = withContext(Dispatchers.IO) {
    val term = URLEncoder.encode("${track.title} ${track.artists.first()}", Charsets.UTF_8)
    val page = readApple(URI("https://music.apple.com/pl/search?term=" + term.replace("+", "%20")))
    for (id in appleSongIds(page, track.title)) {
        val song = readApple(URI("https://music.apple.com/pl/song/-/$id"))
        matchApple(track, id, song)?.let { return@withContext it }
    }
    null
}

private val searchData = Regex("""<script[^>]*\bid="serialized-server-data"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
private val linkedData = Regex("""<script[^>]*\btype="application/ld\+json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.content.orEmpty()

internal fun appleSongIds(page: String, title: String): Set<Long> {
    val data = searchData.find(page)?.groupValues?.get(1) ?: throw IOException("Missing Apple Music search data")
    val ids = linkedSetOf<Long>()
    val expected = normalize(title)
    fun visit(node: JsonElement) {
        when (node) {
            is JsonArray -> node.forEach(::visit)
            is JsonObject -> {
                val descriptor = node["contentDescriptor"] as? JsonObject
                if (descriptor?.text("kind") == "song" && normalize(node.text("title")) == expected) {
                    val identifiers = descriptor["identifiers"] as? JsonObject
                    identifiers?.text("storeAdamID")?.toLongOrNull()?.takeIf { it > 0 }?.let(ids::add)
                }
                node.values.forEach(::visit)
            }
            else -> Unit
        }
    }
    visit(Json.parseToJsonElement(data))
    return ids
}

private fun readApple(url: URI, redirects: Int = 0): String {
    val limit = 4 * 1024 * 1024
    val connection = url.toURL().openConnection() as HttpURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 5_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "text/html")
        if (connection.responseCode in listOf(301, 302, 307, 308) && redirects < 2) {
            val next = url.resolve(connection.getHeaderField("Location") ?: throw IOException("Missing Apple Music redirect"))
            if (next.scheme == "https" && next.rawAuthority == "music.apple.com") return readApple(next, redirects + 1)
        }
        if (connection.responseCode != 200) throw IOException("Apple search unavailable")
        val bytes = connection.inputStream.use { it.readNBytes(limit + 1) }
        if (bytes.size > limit) throw IOException("Unexpected search response")
        return bytes.toString(Charsets.UTF_8)
    } finally {
        connection.disconnect()
    }
}

private val separators = Regex("[^\\p{L}\\p{N}]+")
private fun normalize(value: String) =
    Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase().replace(separators, " ").trim()

internal fun matchApple(track: ImportTrack, id: Long, page: String): Recording? {
    for (script in linkedData.findAll(page)) {
        val composition = Json.parseToJsonElement(script.groupValues[1]) as? JsonObject ?: continue
        val song = composition["audio"] as? JsonObject ?: continue
        if (song.text("@type") != "MusicRecording" || normalize(song.text("name")) != normalize(track.title)) continue
        val artists = song["byArtist"] as? JsonArray ?: continue
        val credits = artists.filterIsInstance<JsonObject>().map { normalize(it.text("name")) }.toSet()
        if (credits != track.artists.map(::normalize).toSet()) continue
        val audio = song["audio"] as? JsonObject ?: continue
        val url = audio.text("contentUrl")
        if (audio.text("@type") == "AudioObject" && isApplePreviewUrl(url)) {
            return Recording("apple-$id", track.title, track.artists.joinToString(" / "), url)
        }
    }
    return null
}
