package pl.tuneprint

import pl.tuneprint.net.RankingPeriod
import pl.tuneprint.net.ImportTrack
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

internal class SpotifyImportException(message: String) : IOException(message)

internal object SpotifyCallback {
    const val redirect = "https://tuneprint.mliczek.com/spotify/callback"
    private val pending = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<String>>()

    suspend fun waitForCode(state: String, launch: suspend () -> Unit): String {
        val response = CompletableDeferred<String>()
        pending[state] = response
        try {
            launch()
            return withTimeoutOrNull(TimeUnit.MINUTES.toMillis(5)) { response.await() }
                ?: throw SpotifyImportException("Upłynął czas logowania. Spróbuj ponownie.")
        } finally {
            pending.remove(state)
        }
    }

    fun receive(address: String): Boolean {
        val url = address.toHttpUrlOrNull() ?: return false
        if (url.newBuilder().query(null).fragment(null).build().toString() != redirect) return false
        val response = pending[url.queryParameterValues("state").singleOrNull() ?: return false] ?: return false
        if (url.queryParameter("error") != null) {
            return response.completeExceptionally(SpotifyImportException("Spotify odmówiło dostępu."))
        }
        val code = url.queryParameterValues("code").singleOrNull()?.takeIf { it.isNotBlank() } ?: return false
        return response.complete(code)
    }
}

internal class SpotifyTopTracks(
    private val clientId: String,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
) {
    suspend fun importTracks(match: suspend (RankingPeriod, Int, List<ImportTrack>) -> Set<String>, openBrowser: (String) -> Unit): List<ImportTrack> {
        if (clientId.isBlank()) throw SpotifyImportException("Brakuje identyfikatora aplikacji Spotify.")
        val caller = currentCoroutineContext()[ContinuationInterceptor] ?: Dispatchers.Main
        return withContext(Dispatchers.IO) {
            val verifier = nonce()
            val state = nonce()
            val code = SpotifyCallback.waitForCode(state) {
                val url = "https://accounts.spotify.com/authorize".toHttpUrl().newBuilder()
                    .addQueryParameter("client_id", clientId)
                    .addQueryParameter("response_type", "code")
                    .addQueryParameter("scope", "user-top-read")
                    .addQueryParameter("redirect_uri", SpotifyCallback.redirect)
                    .addQueryParameter("state", state)
                    .addQueryParameter("code_challenge_method", "S256")
                    .addQueryParameter("code_challenge", verifier.encodeUtf8().sha256().base64Url().trimEnd('='))
                    .build()
                withContext(caller) { openBrowser(url.toString()) }
            }
            val form = FormBody.Builder().add("grant_type", "authorization_code")
                .add("client_id", clientId).add("code", code)
                .add("redirect_uri", SpotifyCallback.redirect).add("code_verifier", verifier).build()
            val token = json(Request.Builder().url("https://accounts.spotify.com/api/token").post(form).build())
                .text("access_token") ?: throw SpotifyImportException("Spotify nie zwróciło tokenu dostępu.")
            val tracks = linkedMapOf<String, ImportTrack>()
            for (period in RankingPeriod.entries) {
                var offset = 0
                var found = 0
                val seen = mutableSetOf<String>()
                while (found < 50) {
                    currentCoroutineContext().ensureActive()
                    val limit = 50 - found
                    val page = json(Request.Builder()
                        .url("https://api.spotify.com/v1/me/top/tracks?time_range=${period.spotify}&limit=$limit&offset=$offset")
                        .header("Authorization", "Bearer $token").build())
                    val items = page["items"] as? JsonArray ?: throw SpotifyImportException("Brak listy utworów w odpowiedzi Spotify.")
                    val candidates = items.take(limit).filterIsInstance<JsonObject>().mapNotNull { item ->
                        val id = item.text("id") ?: return@mapNotNull null
                        val title = item.text("name") ?: return@mapNotNull null
                        val artists = (item["artists"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { it.text("name") }
                        if (artists.isEmpty() || !seen.add(id)) null else ImportTrack(id, title, artists, setOf(period))
                    }
                    if (candidates.isNotEmpty()) {
                        val keys = withContext(caller) { match(period, found, candidates) }
                        for (track in candidates.filter { it.id in keys }) {
                            tracks[track.id] = track.copy(periods = tracks[track.id]?.periods.orEmpty() + period)
                            found++
                        }
                    }
                    offset += items.size
                    if (items.isEmpty() || page.text("next") == null || offset >= (page.text("total")?.toIntOrNull() ?: Int.MAX_VALUE)) break
                }
            }
            tracks.values.toList()
        }
    }

    private fun json(request: Request): JsonObject = http.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw SpotifyImportException(when (response.code) {
                401 -> "Spotify odrzuciło logowanie. Zaloguj się ponownie."
                403 -> "Brak dostępu do Spotify. Sprawdź listę użytkowników aplikacji w Spotify Dashboard."
                429 -> "Spotify ograniczyło liczbę zapytań. Spróbuj później."
                else -> "Błąd Spotify (HTTP ${response.code})."
            })
        }
        Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
    }

    private fun nonce() = ByteArray(32).also(SecureRandom()::nextBytes).toByteString().base64Url().trimEnd('=')
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
