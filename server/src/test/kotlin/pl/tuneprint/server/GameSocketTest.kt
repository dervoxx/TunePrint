package pl.tuneprint.server

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import pl.tuneprint.net.ClientCommand
import pl.tuneprint.net.ServerEvent
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSocketTest {
    @Test
    fun threeConnectionsPlayOneMatchOverJson() = testApplication {
        val now = AtomicLong(0)
        val games = GameServer(now::get)
        application { gameServer(games, AppleCatalog { recording.takeIf { _ -> it == importedTrack } }) }
        val client = createClient { install(WebSockets) }
        val sockets = List(3) { client.webSocketSession("/game") }
        try {
            val host = sockets.first()
            host.send("{")
            assertEquals("error", host.until { true }.type)
            host.command(ClientCommand("create", name = "A", catalogKeys = listOf("demo-01")))
            assertEquals("error", host.until { true }.type)
            host.command(ClientCommand("create", name = "A", tracks = listOf(importedTrack)))
            val admission = host.until { it.token != null }
            val code = admission.state!!.code
            for ((i, peer) in sockets.drop(1).withIndex()) {
                peer.command(ClientCommand("import", tracks = listOf(importedTrack)))
                val keys = peer.until { it.type == "catalog" }.catalogKeys
                assertEquals(listOf(importedTrack.id), keys)
                peer.command(ClientCommand("join", name = "Gość ${i + 1}", code = code, catalogKeys = keys))
                peer.until { it.token != null }
            }
            host.command(ClientCommand("start"))
            val countdown = host.until { it.state?.phase == "COUNTDOWN" }.state!!
            assertNull(countdown.result)
            assertEquals(recording.previewUrl, countdown.previewUrl)
            assertTrue(countdown.audioKey != importedTrack.id && countdown.audioKey != recording.key)
            val ids = countdown.players.map { it.id }
            now.set(3_000)
            for (peer in sockets) {
                peer.command(ClientCommand("answer", roundNumber = 1, ownerIds = ids))
            }
            val results = sockets.map { it.until { event -> event.state?.phase == "FINISHED" }.state!! }
            assertTrue(results.all { it == results.first() })
            assertNull(results.first().previewUrl)
            assertEquals(listOf(100, 100, 100), results.first().ranking.map { it.points })
        } finally {
            sockets.forEach { it.close() }
        }
    }

    private suspend fun WebSocketSession.command(command: ClientCommand) = send(Json.encodeToString(command))

    private suspend fun WebSocketSession.until(matches: (ServerEvent) -> Boolean): ServerEvent = withTimeout(5_000) {
        var event: ServerEvent
        do {
            event = Json.decodeFromString<ServerEvent>((incoming.receive() as Frame.Text).readText())
        } while (!matches(event))
        event
    }
}
