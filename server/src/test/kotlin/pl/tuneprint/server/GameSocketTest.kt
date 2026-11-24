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
import pl.tuneprint.net.RankingPeriod
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

    @Test
    fun hostSelectsWhichRankingsContributeRecordingsAndOwners() = testApplication {
        val now = AtomicLong(0)
        application { gameServer(GameServer(now::get), AppleCatalog { recording.copy(key = it.id) }) }
        val client = createClient { install(WebSockets) }
        val sockets = List(3) { client.webSocketSession("/game") }
        try {
            val host = sockets.first()
            val short = importedTrack.copy(id = "B".repeat(22), title = "Short", periods = setOf(RankingPeriod.SHORT))
            host.command(ClientCommand("create", name = "A", tracks = listOf(importedTrack, short)))
            val admission = host.until { it.token != null }
            for ((i, guest) in sockets.drop(1).withIndex()) {
                guest.command(ClientCommand("join", name = "Guest $i", code = admission.state!!.code,
                    tracks = listOf(short.copy(periods = setOf(RankingPeriod.MEDIUM)))))
                guest.until { it.token != null }
            }
            sockets[1].command(ClientCommand("configure", period = RankingPeriod.SHORT))
            sockets[1].until { it.type == "error" }
            host.command(ClientCommand("configure", period = RankingPeriod.SHORT))
            val lobby = host.until { it.state?.period == RankingPeriod.SHORT }.state!!
            assertEquals(1, lobby.maxRounds)
            host.command(ClientCommand("start"))
            host.until { it.state?.phase == "COUNTDOWN" }
            now.set(3000)
            for (peer in sockets) peer.command(ClientCommand("answer", roundNumber = 1, ownerIds = listOf(admission.playerId!!)))
            val final = host.until { it.state?.phase == "FINISHED" }.state!!
            assertEquals(listOf(admission.playerId), final.result!!.ownerIds)
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
