package pl.tuneprint.server

import pl.tuneprint.net.ClientCommand
import pl.tuneprint.net.ServerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameServerTest {
    private var now = 0L
    private val games = GameServer { now }
    private val keys = DemoCatalog.keys.take(2)

    private inner class Client {
        val events = mutableListOf<ServerEvent>()
        val peer = Peer { events.add(it) }
        val last get() = events.last()
        val state get() = events.last { it.state != null }.state!!
        val id get() = events.first { it.playerId != null }.playerId!!
        val token get() = events.first { it.token != null }.token!!
        fun send(cmd: ClientCommand) = games.command(peer, cmd)
    }

    private fun create(rounds: Int = 1) = Client().apply {
        send(ClientCommand("create", name = "Host", catalogKeys = keys, roundCount = rounds))
    }

    private fun join(host: Client, selectedKeys: List<String> = keys) = Client().apply {
        send(ClientCommand("join", code = host.state.code, name = "Gość", catalogKeys = selectedKeys))
    }

    @Test
    fun validatesAdmissionSettingsAndHostAuthority() {
        val host = create()
        val other = join(host)
        assertNotEquals(host.token, other.token)
        assertTrue(host.events.drop(1).all { it.token == null && it.playerId == null })
        host.send(ClientCommand("start"))
        assertEquals("error", host.last.type)
        other.send(ClientCommand("configure", capacity = 8))
        assertEquals("error", other.last.type)
        host.send(ClientCommand("configure", capacity = 3, roundCount = 999))
        assertEquals("error", host.last.type)
        assertEquals(4, host.state.capacity)
        assertEquals("error", join(host, listOf("bad-key")).last.type)
        val third = join(host)
        host.send(ClientCommand("configure", capacity = 3))
        assertEquals("error", join(host).last.type)
        host.send(ClientCommand("start"))
        other.send(ClientCommand("answer", roundNumber = 1, ownerIds = listOf(other.id)))
        assertEquals("error", other.last.type)
        assertEquals("COUNTDOWN", third.state.phase)
        val visitor = Client()
        visitor.send(ClientCommand("list"))
        assertTrue(visitor.last.rooms.isEmpty())
    }

    @Test
    fun startAvailabilityFollowsConnectedPlayers() {
        val host = create()
        join(host)
        val guest = join(host)
        assertTrue(host.state.canStart)
        games.disconnect(guest.peer)
        assertFalse(host.state.canStart)
        host.send(ClientCommand("start"))
        assertEquals("error", host.last.type)
        Client().send(ClientCommand("resume", token = guest.token))
        assertTrue(host.state.canStart)
    }

    @Test
    fun deadlineUsesLastDraftAndRejectsLateAnswerBeforeNextTick() {
        val host = create()
        val other = join(host)
        val third = join(host)
        host.send(ClientCommand("configure", showOwnerCount = true))
        host.send(ClientCommand("start"))
        assertNull(host.state.result)
        assertNull(host.state.ownerCountHint)
        now = 3_000
        games.tick()
        val ids = host.state.players.map { it.id }
        assertEquals(3, host.state.ownerCountHint)
        other.send(ClientCommand("draft", ownerIds = listOf("unknown"), roundNumber = 1))
        assertEquals("error", other.last.type)
        other.send(ClientCommand("draft", ownerIds = ids, roundNumber = 1))
        third.send(ClientCommand("draft", ownerIds = ids, roundNumber = 1))
        third.send(ClientCommand("draft", ownerIds = emptyList(), roundNumber = 1))
        now += 1_000
        host.send(ClientCommand("answer", ownerIds = ids, roundNumber = 1))
        host.send(ClientCommand("answer", ownerIds = ids, roundNumber = 1))
        assertEquals("error", host.last.type)
        assertEquals(listOf(host.id), third.state.answeredIds)
        assertNull(other.state.result)
        assertTrue(host.state.ranking.all { it.points == 0 })
        now = 18_000
        third.send(ClientCommand("answer", ownerIds = ids, roundNumber = 1))
        assertEquals("error", third.last.type)
        assertEquals("FINISHED", host.state.phase)
        val result = host.state.result!!
        assertEquals(ids.toSet(), result.ownerIds.toSet())
        val submitted = result.answers.associateBy { it.playerId }
        assertEquals(1_000L, submitted.getValue(host.id).elapsedMs)
        assertEquals(15_000L, submitted.getValue(other.id).elapsedMs)
        assertEquals(60, submitted.getValue(other.id).points)
        assertNull(submitted.getValue(third.id).elapsedMs)
        assertEquals(0, submitted.getValue(third.id).points)
        assertEquals(ids, host.state.ranking.map { it.playerId })
        host.send(ClientCommand("replay"))
        assertEquals("LOBBY", other.state.phase)
        assertNull(other.state.result)
        assertTrue(host.state.ranking.all { it.points == 0 })
    }

    @Test
    fun completesEarlyWaitsForReadinessAndDiscardsStaleCommands() {
        val host = create(rounds = 2)
        val clients = listOf(host, join(host), join(host))
        host.send(ClientCommand("configure", mode = "TITLE"))
        host.send(ClientCommand("start"))
        val firstKey = host.state.audioKey
        now += 3_000
        games.tick()
        for (client in clients) client.send(ClientCommand("answer", title = "Próba", roundNumber = 1))
        assertEquals("RESULTS", host.state.phase)
        assertNotNull(host.state.result)
        for (client in clients) client.send(ClientCommand("ready", roundNumber = 1))
        assertEquals("COUNTDOWN", host.state.phase)
        assertNotEquals(firstKey, host.state.audioKey)
        assertNull(host.state.result)
        assertTrue(host.state.answeredIds.isEmpty())
        now += 3_000
        host.send(ClientCommand("answer", roundNumber = 1, title = "Stara odpowiedź"))
        assertEquals("error", host.last.type)
        assertEquals("ROUND", host.state.phase)
        assertTrue(host.state.answeredIds.isEmpty())
        now += 15_000
        games.tick()
        assertEquals("FINISHED", host.state.phase)
    }

    @Test
    fun timerAdvancesThroughResultsWithoutClientActivity() {
        val host = create(rounds = 2)
        join(host)
        join(host)
        host.send(ClientCommand("start"))
        now = 47_999
        games.tick()
        assertEquals("RESULTS", host.state.phase)
        assertEquals(1L, host.state.remainingMs)
        now++
        games.tick()
        assertEquals("COUNTDOWN", host.state.phase)
        assertEquals(2, host.state.roundNumber)
    }

    @Test
    fun resumesSeatAndExpiresDisconnectedHostsAndEmptyRooms() {
        val host = create()
        val other = join(host)
        val code = host.state.code
        val resumed = Client()
        resumed.send(ClientCommand("resume", code = code, token = "incorrect"))
        assertEquals("error", resumed.last.type)
        resumed.send(ClientCommand("resume", code = code, token = host.token))
        assertEquals("left", host.last.type)
        assertEquals(host.id, resumed.id)
        games.disconnect(host.peer)
        host.send(ClientCommand("leave"))
        assertEquals("error", host.last.type)
        games.disconnect(resumed.peer)
        now = 119_999
        games.tick()
        assertEquals(host.id, other.state.hostId)
        now++
        resumed.send(ClientCommand("resume", token = host.token))
        assertEquals("error", resumed.last.type)
        assertEquals(other.id, other.state.hostId)
        assertEquals(1, other.state.players.size)
        games.disconnect(other.peer)
        now += 120_000
        games.tick()
        host.send(ClientCommand("list"))
        assertTrue(host.last.rooms.isEmpty())
        host.send(ClientCommand("resume", token = other.token))
        assertEquals("error", host.last.type)
    }

    @Test
    fun explicitLeaveDuringMatchPreservesOwnersAndReleasesSeatOnReplay() {
        val host = create()
        val other = join(host)
        val third = join(host)
        host.send(ClientCommand("start"))
        now = 3_001
        val ids = host.state.players.map { it.id }
        host.send(ClientCommand("leave"))
        host.send(ClientCommand("resume", token = host.token))
        assertEquals("error", host.last.type)
        for (client in listOf(other, third)) client.send(ClientCommand("answer", ownerIds = ids, roundNumber = 1))
        assertEquals("FINISHED", other.state.phase)
        assertEquals(ids.toSet(), other.state.result!!.ownerIds.toSet())
        other.send(ClientCommand("replay"))
        assertFalse(other.state.players.any { it.id == host.id })
    }
}
