package pl.tuneprint.game

import kotlin.math.roundToInt
import kotlin.random.Random

enum class GamePhase { LOBBY, COUNTDOWN, ROUND, RESULTS, FINISHED }

data class Player(val id: String, val name: String)

data class GameTrack(
    val audioKey: String,
    val title: String,
    val artist: String,
    val ownerId: String,
)

data class PlayerResult(
    val player: Player,
    val selectedOwnerId: String?,
    val elapsedMs: Long?,
    val correct: Boolean,
    val points: Int,
)

data class RankingEntry(val player: Player, val points: Int, val correctAnswers: Int)

data class RoundResult(
    val owner: Player,
    val title: String,
    val artist: String,
    val answers: List<PlayerResult>,
)

data class GameSnapshot(
    val phase: GamePhase,
    val players: List<Player>,
    val roundNumber: Int,
    val totalRounds: Int,
    val remainingMs: Long,
    val countdown: Int,
    val audioKey: String?,
    val selectedOwnerId: String?,
    val answeredPlayerIds: Set<String>,
    val readyPlayerIds: Set<String>,
    val ranking: List<RankingEntry>,
    val result: RoundResult?,
)

/** Local game host. A networked game should obtain these snapshots from the server. */
class GameSession(
    tracks: List<GameTrack>,
    private val random: Random = Random.Default,
) {
    val players = listOf(
        Player("you", "Olek"),
        Player("kacper", "Kacper"),
        Player("kamil", "Kamil"),
        Player("ziobro", "Ziobro"),
    )

    private val tracks = tracks.take(3)
    private var phase = GamePhase.LOBBY
    private var roundIndex = 0
    private var elapsedMs = 0L
    private val answers = mutableMapOf<String, Answer>()
    private val readyPlayers = mutableSetOf<String>()
    private var bots = emptyList<BotAnswer>()
    private var botReadyTimes = emptyMap<String, Long>()
    private var result: RoundResult? = null
    private val scores = players.associate { it.id to 0 }.toMutableMap()
    private val correctCounts = players.associate { it.id to 0 }.toMutableMap()

    init {
        require(this.tracks.size == 3) { "Three audio tracks are required" }
        require(this.tracks.all { track -> players.any { it.id == track.ownerId } }) {
            "Every track must belong to an existing player"
        }
    }

    val snapshot: GameSnapshot
        get() = GameSnapshot(
            phase = phase,
            players = players,
            roundNumber = roundIndex + 1,
            totalRounds = tracks.size,
            remainingMs = (phaseDuration - elapsedMs).coerceAtLeast(0),
            countdown = ((COUNTDOWN_MS - elapsedMs + 999) / 1_000).toInt().coerceIn(1, 3),
            audioKey = if (phase == GamePhase.COUNTDOWN || phase == GamePhase.ROUND) {
                tracks[roundIndex].audioKey
            } else null,
            selectedOwnerId = answers[LOCAL_PLAYER_ID]?.ownerId,
            answeredPlayerIds = answers.keys.toSet(),
            readyPlayerIds = readyPlayers.toSet(),
            ranking = players.map {
                RankingEntry(it, scores.getValue(it.id), correctCounts.getValue(it.id))
            }.sortedWith(compareByDescending<RankingEntry> { it.points }.thenByDescending { it.correctAnswers }),
            result = if (phase == GamePhase.RESULTS || phase == GamePhase.FINISHED) result else null,
        )

    fun start() {
        if (phase != GamePhase.LOBBY) return
        beginCountdown()
    }

    /** Pass foreground elapsed time from a monotonic clock; discard ticks while paused. */
    fun advance(deltaMs: Long) {
        require(deltaMs >= 0) { "Time cannot move backwards" }
        if (phase == GamePhase.LOBBY || phase == GamePhase.FINISHED) return
        elapsedMs = (elapsedMs + deltaMs.coerceAtMost(phaseDuration)).coerceAtMost(phaseDuration)
        when (phase) {
            GamePhase.COUNTDOWN -> if (elapsedMs >= COUNTDOWN_MS) {
                phase = GamePhase.ROUND
                elapsedMs = 0
            }
            GamePhase.ROUND -> {
                bots.filter { it.elapsedMs <= elapsedMs }.forEach {
                    if (it.playerId !in answers) answers[it.playerId] = Answer(it.ownerId, it.elapsedMs)
                }
                if (answers.size == players.size || elapsedMs >= ROUND_MS) finishRound()
            }
            GamePhase.RESULTS -> {
                botReadyTimes.filterValues { it <= elapsedMs }.keys.forEach(readyPlayers::add)
                if (readyPlayers.size == players.size || elapsedMs >= RESULTS_MS) nextRound()
            }
            else -> Unit
        }
    }

    fun answer(ownerId: String) {
        if (phase != GamePhase.ROUND || elapsedMs >= ROUND_MS) return
        if (players.none { it.id == ownerId }) return
        if (LOCAL_PLAYER_ID !in answers) answers[LOCAL_PLAYER_ID] = Answer(ownerId, elapsedMs)
        if (answers.size == players.size) finishRound()
    }

    fun ready() {
        if (phase != GamePhase.RESULTS) return
        readyPlayers.add(LOCAL_PLAYER_ID)
        if (readyPlayers.size == players.size) nextRound()
    }

    private fun beginCountdown() {
        phase = GamePhase.COUNTDOWN
        elapsedMs = 0
        answers.clear()
        readyPlayers.clear()
        result = null
        val correctOwnerId = tracks[roundIndex].ownerId
        // Each round has both a correct and an incorrect bot answer, with varied timing.
        val correctness = listOf(true, false, random.nextBoolean()).shuffled(random)
        bots = players.drop(1).mapIndexed { index, player ->
            BotAnswer(
                playerId = player.id,
                ownerId = if (correctness[index]) correctOwnerId else {
                    players.filter { it.id != correctOwnerId }.random(random).id
                },
                elapsedMs = random.nextLong(3_200, 11_501),
            )
        }
    }

    private fun finishRound() {
        val track = tracks[roundIndex]
        val roundAnswers = players.map { player ->
            val answer = answers[player.id]
            val correct = answer?.ownerId == track.ownerId
            val points = if (correct) pointsFor(answer!!.elapsedMs) else 0
            scores[player.id] = scores.getValue(player.id) + points
            if (correct) correctCounts[player.id] = correctCounts.getValue(player.id) + 1
            PlayerResult(player, answer?.ownerId, answer?.elapsedMs, correct, points)
        }
        result = RoundResult(players.first { it.id == track.ownerId }, track.title, track.artist, roundAnswers)
        phase = GamePhase.RESULTS
        elapsedMs = 0
        readyPlayers.clear()
        botReadyTimes = players.drop(1).associate { it.id to random.nextLong(3_500, 9_501) }
    }

    private fun nextRound() {
        if (roundIndex == tracks.lastIndex) {
            phase = GamePhase.FINISHED
            elapsedMs = 0
        } else {
            roundIndex++
            beginCountdown()
        }
    }

    private val phaseDuration: Long
        get() = when (phase) {
            GamePhase.COUNTDOWN -> COUNTDOWN_MS
            GamePhase.ROUND -> ROUND_MS
            GamePhase.RESULTS -> RESULTS_MS
            else -> 0
        }

    private data class Answer(val ownerId: String, val elapsedMs: Long)
    private data class BotAnswer(val playerId: String, val ownerId: String, val elapsedMs: Long)

    companion object {
        const val LOCAL_PLAYER_ID = "you"
        const val COUNTDOWN_MS = 3_000L
        const val ROUND_MS = 15_000L
        const val RESULTS_MS = 30_000L

        fun pointsFor(elapsedMs: Long): Int =
            (100 - 75 * elapsedMs.coerceIn(0, ROUND_MS).toDouble() / ROUND_MS).roundToInt()
    }
}
