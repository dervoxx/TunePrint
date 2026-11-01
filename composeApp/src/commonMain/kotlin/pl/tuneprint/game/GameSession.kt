package pl.tuneprint.game

import kotlin.math.roundToInt
import kotlin.random.Random

enum class GamePhase { LOBBY, COUNTDOWN, ROUND, RESULTS, FINISHED }

data class Player(val id: String, val name: String)

data class GameTrack(
    val audioKey: String,
    val title: String,
    val artist: String,
    val ownerIds: Set<String>,
)

data class PlayerResult(
    val player: Player,
    val selectedOwnerIds: Set<String>?,
    val elapsedMs: Long?,
    val correct: Boolean,
    val accuracy: Double,
    val points: Int,
)

data class RankingEntry(val player: Player, val points: Int, val correctAnswers: Int)

data class RoundResult(
    val owners: List<Player>,
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
    val selectedOwnerIds: Set<String>,
    val answeredPlayerIds: Set<String>,
    val readyPlayerIds: Set<String>,
    val ranking: List<RankingEntry>,
    val result: RoundResult?,
)

/** Local game host. A networked game should obtain these snapshots from the server. */
class GameSession(
    tracks: List<GameTrack>,
    localName: String = "Olek",
    private val random: Random = Random.Default,
    participants: List<Player>? = null,
) {
    val players = participants?.toList() ?: listOf(
        Player("you", localName.trim().take(20).ifBlank { "Olek" }),
        Player("kacper", "Kacper"),
        Player("kamil", "Kamil"),
        Player("ziobro", "Ziobro"),
    )

    private val tracks = tracks.map { it.copy(ownerIds = it.ownerIds.toSet()) }
    private var phase = GamePhase.LOBBY
    private var roundIndex = 0
    private var elapsedMs = 0L
    private val answers = mutableMapOf<String, Answer>()
    private val draftOwnerIds = mutableSetOf<String>()
    private val readyPlayers = mutableSetOf<String>()
    private var bots = emptyList<BotAnswer>()
    private var botReadyTimes = emptyMap<String, Long>()
    private var result: RoundResult? = null
    private val scores = players.associate { it.id to 0 }.toMutableMap()
    private val correctCounts = players.associate { it.id to 0 }.toMutableMap()

    init {
        require(players.size in 3..8 && players.map { it.id }.toSet().size == players.size) {
            "A game requires three to eight distinct players"
        }
        require(players.first().id == LOCAL_PLAYER_ID) {
            "The local player must be first in the roster"
        }
        require(this.tracks.isNotEmpty()) { "At least one audio track is required" }
        require(this.tracks.map { it.audioKey }.toSet().size == this.tracks.size) {
            "Audio tracks cannot repeat within a game"
        }
        require(this.tracks.all { track ->
            track.ownerIds.isNotEmpty() && track.ownerIds.all { id -> players.any { it.id == id } }
        }) {
            "Every track must belong to at least one existing player"
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
            selectedOwnerIds = draftOwnerIds.toSet(),
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
                    if (it.playerId !in answers) answers[it.playerId] = Answer(it.ownerIds, it.elapsedMs)
                }
                if (elapsedMs >= ROUND_MS && LOCAL_PLAYER_ID !in answers && draftOwnerIds.isNotEmpty()) {
                    answers[LOCAL_PLAYER_ID] = Answer(draftOwnerIds.toSet(), ROUND_MS)
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

    fun toggleOwner(ownerId: String) {
        if (phase != GamePhase.ROUND || elapsedMs >= ROUND_MS || LOCAL_PLAYER_ID in answers) return
        if (players.none { it.id == ownerId }) return
        if (!draftOwnerIds.add(ownerId)) draftOwnerIds.remove(ownerId)
    }

    fun submitAnswer() = answer(draftOwnerIds)

    fun answer(ownerIds: Set<String>) {
        if (phase != GamePhase.ROUND || elapsedMs >= ROUND_MS || LOCAL_PLAYER_ID in answers) return
        if (ownerIds.isEmpty() || ownerIds.any { id -> players.none { it.id == id } }) return
        val selection = ownerIds.toSet()
        draftOwnerIds.clear()
        draftOwnerIds.addAll(selection)
        answers[LOCAL_PLAYER_ID] = Answer(selection, elapsedMs)
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
        draftOwnerIds.clear()
        readyPlayers.clear()
        result = null
        val correctOwnerIds = tracks[roundIndex].ownerIds
        // Incorrect bots miss an owner or add one; correct bots submit the complete set.
        val correctness = (listOf(true, false) + List(players.size - 3) { random.nextBoolean() }).shuffled(random)
        bots = players.drop(1).mapIndexed { index, player ->
            BotAnswer(
                playerId = player.id,
                ownerIds = if (correctness[index]) correctOwnerIds else {
                    val missingOwner = correctOwnerIds.toList().random(random)
                    if (correctOwnerIds.size > 1 && random.nextBoolean()) {
                        correctOwnerIds - missingOwner
                    } else {
                        val nonOwners = players.filter { it.id !in correctOwnerIds }
                        if (nonOwners.isNotEmpty()) correctOwnerIds + nonOwners.random(random).id
                        else correctOwnerIds - missingOwner
                    }
                },
                elapsedMs = random.nextLong(3_200, 11_501),
            )
        }
    }

    private fun finishRound() {
        val track = tracks[roundIndex]
        val roundAnswers = players.map { player ->
            val answer = answers[player.id]
            val correct = answer?.ownerIds == track.ownerIds
            val hits = answer?.ownerIds?.count { it in track.ownerIds } ?: 0
            val wrongSelections = answer?.ownerIds?.count { it !in track.ownerIds } ?: 0
            val nonOwnerCount = players.size - track.ownerIds.size
            val wrongPenalty = if (nonOwnerCount > 0) wrongSelections.toDouble() / nonOwnerCount else 0.0
            val accuracy = (hits.toDouble() / track.ownerIds.size - wrongPenalty).coerceIn(0.0, 1.0)
            val points = if (answer != null) pointsFor(answer.elapsedMs, accuracy) else 0
            scores[player.id] = scores.getValue(player.id) + points
            if (correct) correctCounts[player.id] = correctCounts.getValue(player.id) + 1
            PlayerResult(player, answer?.ownerIds, answer?.elapsedMs, correct, accuracy, points)
        }
        result = RoundResult(players.filter { it.id in track.ownerIds }, track.title, track.artist, roundAnswers)
        phase = if (roundIndex == tracks.lastIndex) GamePhase.FINISHED else GamePhase.RESULTS
        elapsedMs = 0
        readyPlayers.clear()
        botReadyTimes = if (phase == GamePhase.RESULTS) {
            players.drop(1).associate { it.id to random.nextLong(3_500, 9_501) }
        } else emptyMap()
    }

    private fun nextRound() {
        roundIndex++
        beginCountdown()
    }

    private val phaseDuration: Long
        get() = when (phase) {
            GamePhase.COUNTDOWN -> COUNTDOWN_MS
            GamePhase.ROUND -> ROUND_MS
            GamePhase.RESULTS -> RESULTS_MS
            else -> 0
        }

    private data class Answer(val ownerIds: Set<String>, val elapsedMs: Long)
    private data class BotAnswer(val playerId: String, val ownerIds: Set<String>, val elapsedMs: Long)

    companion object {
        const val LOCAL_PLAYER_ID = "you"
        const val COUNTDOWN_MS = 3_000L
        const val ROUND_MS = 15_000L
        const val RESULTS_MS = 30_000L

        fun pointsFor(elapsedMs: Long, accuracy: Double = 1.0): Int =
            (accuracy.coerceIn(0.0, 1.0) *
                (60 + 40 * (1 - elapsedMs.coerceIn(0, ROUND_MS).toDouble() / ROUND_MS))).roundToInt()
    }
}
