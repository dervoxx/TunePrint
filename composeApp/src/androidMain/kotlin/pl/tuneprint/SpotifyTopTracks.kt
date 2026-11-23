package pl.tuneprint

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.coroutines.ContinuationInterceptor
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.buffer
import okio.source

internal data class SpotifyTrack(val id: String, val title: String, val artists: List<String>, val isrc: String?)

internal class SpotifyImportException(message: String) : IOException(message)

internal class SpotifyTopTracks(
    private val clientId: String,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
) {
    suspend fun importTracks(openBrowser: (String) -> Unit): List<SpotifyTrack> {
        if (clientId.isBlank()) throw SpotifyImportException("Brakuje identyfikatora aplikacji Spotify.")
        val caller = currentCoroutineContext()[ContinuationInterceptor] ?: Dispatchers.Main
        return withContext(Dispatchers.IO) {
            val verifier = nonce()
            val state = nonce()
            val codeAndRedirect = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { listener ->
                listener.soTimeout = 400
                val redirect = "http://127.0.0.1:${listener.localPort}/spotify/callback".toHttpUrl()
                val url = "https://accounts.spotify.com/authorize".toHttpUrl().newBuilder()
                    .addQueryParameter("client_id", clientId)
                    .addQueryParameter("response_type", "code")
                    .addQueryParameter("scope", "user-top-read")
                    .addQueryParameter("redirect_uri", redirect.toString())
                    .addQueryParameter("state", state)
                    .addQueryParameter("code_challenge_method", "S256")
                    .addQueryParameter("code_challenge", verifier.encodeUtf8().sha256().base64Url().trimEnd('='))
                    .build()
                withContext(caller) { openBrowser(url.toString()) }
                awaitCode(listener, redirect, state) to redirect
            }
            val form = FormBody.Builder().add("grant_type", "authorization_code")
                .add("client_id", clientId).add("code", codeAndRedirect.first)
                .add("redirect_uri", codeAndRedirect.second.toString()).add("code_verifier", verifier).build()
            val token = json(Request.Builder().url("https://accounts.spotify.com/api/token").post(form).build())
                .text("access_token") ?: throw SpotifyImportException("Spotify nie zwróciło tokenu dostępu.")
            val tracks = linkedMapOf<String, SpotifyTrack>()
            var offset = 0
            while (offset < 50) {
                currentCoroutineContext().ensureActive()
                val page = json(Request.Builder()
                    .url("https://api.spotify.com/v1/me/top/tracks?time_range=medium_term&limit=${50 - offset}&offset=$offset")
                    .header("Authorization", "Bearer $token").build())
                val items = page["items"] as? JsonArray ?: throw SpotifyImportException("Brak listy utworów w odpowiedzi Spotify.")
                for (item in items.take(50 - offset).filterIsInstance<JsonObject>()) {
                    val id = item.text("id") ?: continue
                    val title = item.text("name") ?: continue
                    val artists = (item["artists"] as? JsonArray).orEmpty()
                        .filterIsInstance<JsonObject>().mapNotNull { it.text("name") }
                    if (artists.isEmpty()) continue
                    tracks.putIfAbsent(id, SpotifyTrack(id, title, artists, (item["external_ids"] as? JsonObject)?.text("isrc")))
                }
                if (items.isEmpty() || page.text("next") == null) break
                offset += items.size
            }
            tracks.values.toList()
        }
    }

    private suspend fun awaitCode(listener: ServerSocket, redirect: HttpUrl, state: String): String {
        val start = System.nanoTime()
        while (System.nanoTime() - start < TimeUnit.MINUTES.toNanos(5)) {
            currentCoroutineContext().ensureActive()
            val socket = try { listener.accept() } catch (_: SocketTimeoutException) { continue }
            socket.use {
                socket.soTimeout = 1500
                val target = try {
                    val source = socket.source().buffer()
                    source.timeout().deadline(2, TimeUnit.SECONDS)
                    val request = source.readUtf8LineStrict(8192).split(' ')
                    var remaining = 16384L
                    while (true) {
                        val header = source.readUtf8LineStrict(remaining)
                        if (header.isEmpty()) break
                        remaining -= header.length + 2L
                        if (remaining <= 0) throw IOException()
                    }
                    request.getOrNull(1)?.takeIf { request.first() == "GET" && it.startsWith("/spotify/callback?") }
                } catch (_: IOException) { null }
                val url = target?.let { redirect.resolve(it) }
                val valid = url != null && url.queryParameterValues("state") == listOf(state)
                val code = url?.queryParameterValues("code")?.singleOrNull()?.takeIf { it.isNotBlank() }
                val denied = url?.queryParameter("error") != null
                val accepted = valid && (denied || code != null)
                val message = if (accepted) "Wróć do aplikacji TunePrint." else "Nieprawidłowe przekierowanie."
                val response = if (accepted) "200 OK" else "400 Bad Request"
                runCatching {
                    socket.getOutputStream().write(
                        ("HTTP/1.1 $response\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\n" +
                            "Connection: close\r\nContent-Length: ${message.toByteArray().size}\r\n\r\n$message").toByteArray()
                    )
                }
                if (accepted && denied) throw SpotifyImportException("Logowanie Spotify zostało odrzucone.")
                if (accepted && code != null) return code
            }
        }
        throw SpotifyImportException("Upłynął czas logowania. Spróbuj ponownie.")
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
