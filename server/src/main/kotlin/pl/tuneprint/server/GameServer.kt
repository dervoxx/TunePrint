package pl.tuneprint.server

import pl.tuneprint.net.ClientCommand
import pl.tuneprint.net.NetPlayer
import pl.tuneprint.net.RoomListing
import pl.tuneprint.net.ServerEvent
import java.util.UUID
import kotlin.random.Random

internal class Peer(val send: (ServerEvent) -> Unit) {
    var roomCode: String? = null
    fun error(message: String) = send(ServerEvent("error", message = message))
}

internal class Seat(name: String, val catalog: List<Recording>, var peer: Peer?) {
    val player = NetPlayer(UUID.randomUUID().toString(), name)
    var token: String? = UUID.randomUUID().toString()
    var disconnectedAt: Long? = null
}

/** Commits membership, game transitions and event order under the same lock.
 * Peer.send only enqueues. Socket IO happens outside this lock. */
internal class GameServer(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val rooms = mutableMapOf<String, GameRoom>()

    @Synchronized
    fun command(peer: Peer, cmd: ClientCommand, catalog: Map<String, Recording> = emptyMap()) {
        val now = clock()
        try {
            val room = rooms[peer.roomCode]
            if (room != null && refresh(room, now)) broadcast(room, now)
            when (cmd.type) {
                "list" -> {
                    val listings = rooms.values.filter { it.canJoin }.map {
                        RoomListing(it.code, it.seats.getValue(it.hostId).player.name, it.roundCount)
                    }
                    peer.send(ServerEvent("rooms", rooms = listings))
                }
                "create", "join", "resume" -> {
                    require(peer.roomCode == null) { "Jesteś już w pokoju." }
                    if (cmd.type == "resume") resume(peer, cmd, now) else admit(peer, cmd, now, catalog)
                }
                else -> {
                    requireNotNull(room) { "Dołącz do pokoju." }
                    val seat = requireNotNull(room.seats.values.find { it.peer === peer }) { "Sesja wygasła." }
                    if (cmd.type == "leave") {
                        peer.roomCode = null
                        room.withdraw(seat, now)
                        peer.send(ServerEvent("left"))
                        if (room.empty) rooms.remove(room.code) else broadcast(room, now)
                    } else if (room.command(seat, cmd, now)) {
                        broadcast(room, now)
                    }
                }
            }
        } catch (invalid: IllegalArgumentException) {
            peer.error(invalid.message ?: "Niepoprawne polecenie.")
        }
    }

    private fun admit(peer: Peer, cmd: ClientCommand, now: Long, catalog: Map<String, Recording>) {
        val name = cmd.name.orEmpty().trim()
        require(name.length in 1..20 && name.none(Char::isISOControl)) { "Podaj nazwę od 1 do 20 znaków." }
        val keys = cmd.catalogKeys?.toSet() ?: catalog.keys
        require(keys.isNotEmpty() && catalog.keys.containsAll(keys)) { "Do gry potrzeba utworów Spotify z dostępnym fragmentem Apple." }
        val seat = Seat(name, keys.map(catalog::getValue).distinctBy { it.key }, peer)
        val room = if (cmd.type == "create") {
            require(rooms.size < 128) { "Osiągnięto limit pokoi." }
            val code = generateSequence { Random.nextInt(100_000, 1_000_000).toString() }.first { it !in rooms }
            GameRoom(code, seat).also {
                it.configure(cmd)
                rooms[code] = it
            }
        } else {
            val target = requireNotNull(rooms[cmd.code]) { "Nie znaleziono pokoju." }
            if (refresh(target, now)) broadcast(target, now)
            require(!target.empty && target.canJoin) { "Pokój jest pełny lub gra już trwa." }
            target.seats[seat.player.id] = seat
            target
        }
        peer.roomCode = room.code
        broadcast(room, now, seat)
    }

    private fun resume(peer: Peer, cmd: ClientCommand, now: Long) {
        val token = requireNotNull(cmd.token) { "Brak tokenu sesji." }
        val room = if (cmd.code != null) rooms[cmd.code] else rooms.values.find { r ->
            r.seats.values.any { it.token == token }
        }
        requireNotNull(room) { "Sesja wygasła." }
        if (refresh(room, now)) broadcast(room, now)
        val seat = requireNotNull(room.seats.values.find { it.token == token }) { "Sesja wygasła." }
        seat.peer?.let { previous ->
            previous.roomCode = null
            previous.send(ServerEvent("left"))
        }
        seat.peer = peer
        seat.disconnectedAt = null
        peer.roomCode = room.code
        broadcast(room, now, seat)
    }

    @Synchronized
    fun disconnect(peer: Peer) {
        val room = rooms[peer.roomCode] ?: return
        peer.roomCode = null
        val seat = room.seats.values.find { it.peer === peer } ?: return
        seat.peer = null
        val now = clock()
        seat.disconnectedAt = now
        broadcast(room, now)
    }

    @Synchronized
    fun tick() {
        val now = clock()
        for (room in rooms.values.toList()) {
            val changed = refresh(room, now)
            if (!room.empty && (changed || room.timed)) broadcast(room, now)
        }
    }

    private fun refresh(room: GameRoom, now: Long): Boolean {
        val changed = room.advance(now)
        val expired = room.seats.values.filter { seat ->
            seat.disconnectedAt?.let { now - it >= 120_000 } == true
        }
        expired.forEach { room.withdraw(it, now) }
        if (room.empty) rooms.remove(room.code)
        return changed || expired.isNotEmpty()
    }

    private fun broadcast(room: GameRoom, now: Long, admitted: Seat? = null) {
        val state = room.state(now)
        for (seat in room.seats.values) {
            val event = if (seat === admitted) {
                ServerEvent("state", state = state, playerId = seat.player.id, token = seat.token)
            } else ServerEvent("state", state = state)
            seat.peer?.send(event)
        }
    }
}
