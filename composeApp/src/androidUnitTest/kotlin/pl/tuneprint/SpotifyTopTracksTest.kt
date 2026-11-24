package pl.tuneprint

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.encodeUtf8

class SpotifyTopTracksTest {
    private val clientId = "0".repeat(32)

    @Test fun loginValidatesStateAndVerifierThenImportsTwoPages() = runBlocking {
        var redirect = ""
        var challenge = ""
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val json = if (url.host == "accounts.spotify.com") {
                assertNull(request.header("Authorization"))
                val form = request.body as FormBody
                val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
                assertEquals(setOf("grant_type", "client_id", "code", "redirect_uri", "code_verifier"), fields.keys)
                assertEquals(challenge, fields.getValue("code_verifier").encodeUtf8().sha256().base64Url().trimEnd('='))
                assertEquals(redirect, fields["redirect_uri"])
                assertEquals("one-use-code", fields["code"])
                assertEquals(clientId, fields["client_id"])
                """{"access_token":"test-token"}"""
            } else {
                assertEquals("api.spotify.com", url.host)
                assertEquals("Bearer test-token", request.header("Authorization"))
                val period = url.queryParameter("time_range")!!
                val offset = url.queryParameter("offset")!!
                requests += "$period/$offset"
                assertEquals(if (offset == "0") "50" else "1", url.queryParameter("limit"))
                if (offset == "0") {
                    val items = (1..50).joinToString { id ->
                        """{"id":"$id","name":"Track $id","artists":[{"name":"A"}]}"""
                    }
                    """{"next":"https://other.invalid/untrusted","items":[$items]}"""
                } else {
                    assertEquals("50", offset)
                    """{"next":"more","items":[{"id":"51","name":"Track 51","artists":[{"name":"A"}]}]}"""
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()
        val opened = CompletableDeferred<String>()
        val browser = async(Dispatchers.IO) {
            val login = opened.await().toHttpUrl()
            assertEquals("S256", login.queryParameter("code_challenge_method"))
            assertEquals("user-top-read", login.queryParameter("scope"))
            redirect = login.queryParameter("redirect_uri")!!
            assertEquals(SpotifyCallback.redirect, redirect)
            challenge = login.queryParameter("code_challenge")!!
            assertEquals(false, SpotifyCallback.receive("$redirect?code=bad&state=wrong"))
            assertEquals(true, SpotifyCallback.receive("$redirect?code=one-use-code&state=${login.queryParameter("state")}"))
        }
        val tracks = SpotifyTopTracks(clientId, client).importTracks({ _, _, page -> page.map { it.id }.filterNot { it == "1" }.toSet() }) { opened.complete(it) }
        browser.await()
        assertEquals(pl.tuneprint.net.RankingPeriod.entries.flatMap { listOf("${it.spotify}/0", "${it.spotify}/50") }, requests)
        assertEquals((2..51).map { it.toString() }, tracks.map { it.id })
        assertEquals(pl.tuneprint.net.RankingPeriod.entries.toSet(), tracks.first().periods)
    }

    @Test fun userCanDenyAuthorization() = runBlocking {
        val opened = CompletableDeferred<String>()
        val browser = async(Dispatchers.IO) {
            val login = opened.await().toHttpUrl()
            SpotifyCallback.receive("${login.queryParameter("redirect_uri")}?error=access_denied&state=${login.queryParameter("state")}")
        }
        assertFailsWith<SpotifyImportException> {
            SpotifyTopTracks(clientId).importTracks({ _, _, _ -> emptySet() }) { opened.complete(it) }
        }
        assertEquals(true, browser.await())
    }

    @Test fun cancellingImportRejectsLateCallback() = runBlocking {
        val opened = CompletableDeferred<String>()
        val import = async { SpotifyTopTracks(clientId).importTracks({ _, _, _ -> emptySet() }) { opened.complete(it) } }
        val login = opened.await().toHttpUrl()
        import.cancelAndJoin()
        assertEquals(false, SpotifyCallback.receive("${SpotifyCallback.redirect}?code=late&state=${login.queryParameter("state")}"))
    }
}
