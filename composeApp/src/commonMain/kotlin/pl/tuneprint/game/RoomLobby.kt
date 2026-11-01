package pl.tuneprint.game

import kotlin.random.Random

enum class RoomRoute { HOME, JOIN, LOBBY, GAME }

data class AvailableRoom(val code: String, val hostName: String, val roundCount: Int)

data class RoomSnapshot(
    val route: RoomRoute,
    val code: String,
    val players: List<Player>,
    val canStart: Boolean,
    val isHost: Boolean,
    val roundCount: Int,
    val maxRounds: Int,
    val availableRooms: List<AvailableRoom>,
    val joinError: String?,
    val capacity: Int,
    val showOwnerCount: Boolean,
)

/** Local room membership; a future network host supplies room codes and join events. */
class RoomLobby(
    players: List<Player>,
    private val random: Random = Random.Default,
    val maxRounds: Int = 10,
) {
    private val roster = players.toList()
    private var route = RoomRoute.HOME
    private var code = ""
    private var joinedCount = 0
    private var arrivalElapsedMs = 0L
    private var awaitingPlayers = false
    private var joinError: String? = null
    private var isHost = false
    private var roundCount = 3.coerceAtMost(maxRounds)
    private var capacity = 4
    private var showOwnerCount = false

    init {
        require(maxRounds >= 1) { "At least one round must be available" }
        require(roster.size in 3..8 && roster.map { it.id }.toSet().size == roster.size) {
            "A room requires three to eight distinct players"
        }
        require(roster.first().id == GameSession.LOCAL_PLAYER_ID) {
            "The local player must be first in the roster"
        }
    }

    private val availableRooms = listOf(
        AvailableRoom("111222", "Kacper", 3.coerceAtMost(maxRounds)),
        AvailableRoom("333444", "Kamil", 5.coerceAtMost(maxRounds)),
        AvailableRoom("555666", "Ziobro", 7.coerceAtMost(maxRounds)),
    )

    val snapshot: RoomSnapshot
        get() = RoomSnapshot(
            route = route,
            code = code,
            players = roster.take(minOf(joinedCount, capacity)),
            canStart = route == RoomRoute.LOBBY && minOf(joinedCount, capacity) >= 3,
            isHost = isHost,
            roundCount = roundCount,
            maxRounds = maxRounds,
            availableRooms = availableRooms,
            joinError = joinError,
            capacity = capacity,
            showOwnerCount = showOwnerCount,
        )

    fun create() {
        if (route != RoomRoute.HOME) return
        do {
            code = random.nextInt(100_000, 1_000_000).toString()
        } while (availableRooms.any { it.code == code })
        joinError = null
        isHost = true
        capacity = 4
        showOwnerCount = false
        roundCount = 3.coerceAtMost(maxRounds)
        route = RoomRoute.LOBBY
        joinedCount = 1
        arrivalElapsedMs = 0
        awaitingPlayers = true
    }

    fun openJoin() {
        if (route != RoomRoute.HOME) return
        route = RoomRoute.JOIN
        isHost = false
        capacity = 4
        showOwnerCount = false
        roundCount = 3.coerceAtMost(maxRounds)
        code = ""
        joinError = null
    }

    fun editCode(value: String) {
        if (route != RoomRoute.JOIN) return
        code = value.filter { it in '0'..'9' }.take(6)
        joinError = null
    }

    fun join() {
        if (route != RoomRoute.JOIN) return
        val room = availableRooms.firstOrNull { it.code == code }
        if (room == null) {
            joinError = "Nie znaleziono pokoju"
            return
        }
        isHost = false
        capacity = 4
        showOwnerCount = false
        roundCount = room.roundCount
        joinError = null
        route = RoomRoute.LOBBY
        joinedCount = roster.size
        awaitingPlayers = false
    }

    fun leave() {
        route = RoomRoute.HOME
        joinError = null
        isHost = false
        capacity = 4
        showOwnerCount = false
        roundCount = 3.coerceAtMost(maxRounds)
        code = ""
        joinedCount = 0
        arrivalElapsedMs = 0
        awaitingPlayers = false
    }

    fun setRoundCount(value: Int) {
        if (route != RoomRoute.LOBBY || !isHost) return
        roundCount = value.coerceIn(1, maxRounds)
    }

    fun setCapacity(value: Int) {
        if (route != RoomRoute.LOBBY || !isHost) return
        capacity = value.coerceIn(3, 8)
    }

    fun setShowOwnerCount(value: Boolean) {
        if (route != RoomRoute.LOBBY || !isHost) return
        showOwnerCount = value
    }

    fun start() {
        if (snapshot.canStart) route = RoomRoute.GAME
    }

    fun replay() {
        if (route != RoomRoute.GAME) return
        route = RoomRoute.LOBBY
        joinedCount = roster.size
        awaitingPlayers = false
    }

    /** Advance only by foreground elapsed time, as for the game clock. */
    fun advance(deltaMs: Long) {
        require(deltaMs >= 0) { "Time cannot move backwards" }
        if (route != RoomRoute.LOBBY || !awaitingPlayers) return
        val arrivals = ARRIVAL_TIMES.take(roster.size - 1)
        val lastArrival = arrivals.last()
        arrivalElapsedMs = (arrivalElapsedMs + deltaMs.coerceAtMost(lastArrival)).coerceAtMost(lastArrival)
        joinedCount = 1 + arrivals.count { it <= arrivalElapsedMs }
        if (joinedCount == roster.size) awaitingPlayers = false
    }

    private companion object {
        val ARRIVAL_TIMES = listOf(900L, 1_800L, 3_000L, 4_200L, 5_400L, 6_600L, 7_800L)
    }
}
