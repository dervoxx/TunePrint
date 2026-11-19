package pl.tuneprint.server

import pl.tuneprint.net.ClientCommand
import pl.tuneprint.net.NetAnswer
import pl.tuneprint.net.NetRank
import pl.tuneprint.net.NetResult
import pl.tuneprint.net.RoomState

private enum class Phase { LOBBY, COUNTDOWN, ROUND, RESULTS, FINISHED }
private const val COUNTDOWN_MS = 3_000L
private const val ROUND_MS = 15_000L
private const val RESULTS_MS = 30_000L

internal class GameRoom(val code: String, first: Seat) {
    val seats = linkedMapOf(first.player.id to first)
    var hostId = first.player.id
        private set
    var roundCount = minOf(3, first.catalog.size)
        private set
    private var capacity = 4
    private var mode = "OWNERS"
    private var showOwnerCount = false
    private var phase = Phase.LOBBY
    private var roundNumber = 0
    private var deadline = 0L
    private var tracks = emptyList<CatalogTrack>()
    private val track get() = tracks[roundNumber - 1]
    private fun pool(selectedMode: String) = DemoCatalog.pool(seats.mapValues { it.value.catalog })
        .filter { RoundScoring.isEligible(selectedMode, it, seats.keys) }
    private val maxRounds get() = pool(mode).size
    val canJoin get() = phase == Phase.LOBBY && seats.size < capacity
    val empty get() = seats.values.none { it.token != null }
    val timed get() = phase == Phase.COUNTDOWN || phase == Phase.ROUND || phase == Phase.RESULTS

    private data class Guess(val owners: Set<String>, val text: String?, val elapsed: Long)
    private val drafts = mutableMapOf<String, Guess>()
    private val answers = mutableMapOf<String, Guess>()
    private val ready = mutableSetOf<String>()
    private val totals = mutableMapOf<String, NetRank>()
    private var result: NetResult? = null

    fun configure(cmd: ClientCommand) {
        require(phase == Phase.LOBBY) { "Ustawienia można zmienić w lobby." }
        val limit = cmd.capacity ?: capacity
        val selectedMode = cmd.mode ?: mode
        require(limit in 3..8 && limit >= seats.size) { "Niepoprawny limit graczy." }
        require(selectedMode in setOf("OWNERS", "TITLE", "ARTIST")) { "Nieznany tryb gry." }
        val available = pool(selectedMode).size
        require(available > 0) { "W katalogach brakuje utworów dla tego trybu." }
        val rounds = cmd.roundCount ?: roundCount.coerceAtMost(available)
        require(rounds in 1..available) { "Niepoprawna liczba rund." }
        capacity = limit
        roundCount = rounds
        mode = selectedMode
        showOwnerCount = cmd.showOwnerCount ?: showOwnerCount
    }

    fun command(seat: Seat, cmd: ClientCommand, now: Long): Boolean {
        val id = seat.player.id
        when (cmd.type) {
            "configure", "start", "replay" -> {
                require(id == hostId) { "Tylko gospodarz może to zrobić." }
                when (cmd.type) {
                    "configure" -> configure(cmd)
                    "start" -> {
                        require(phase == Phase.LOBBY && seats.values.count { it.peer != null } >= 3) {
                            "Do startu potrzeba co najmniej trzech połączonych graczy w lobby."
                        }
                        tracks = pool(mode).shuffled().take(roundCount)
                        nextRound(now)
                    }
                    "replay" -> {
                        require(phase == Phase.FINISHED) { "Rozgrywka jeszcze trwa." }
                        seats.values.removeAll { it.token == null }
                        fitSettingsToRoster()
                        phase = Phase.LOBBY
                        roundNumber = 0
                        tracks = emptyList()
                        totals.clear()
                        clearRound()
                    }
                }
            }
            "draft", "answer" -> {
                require(phase == Phase.ROUND && cmd.roundNumber == roundNumber) { "Ta runda nie przyjmuje odpowiedzi." }
                require(id !in answers) { "Odpowiedź została już zatwierdzona." }
                val owners = if (mode == "OWNERS") cmd.ownerIds.orEmpty().toSet() else emptySet()
                require(seats.keys.containsAll(owners)) { "Nieznany właściciel." }
                val text = when (mode) {
                    "TITLE" -> cmd.title
                    "ARTIST" -> cmd.artist
                    else -> null
                }?.trim()?.takeIf { it.isNotEmpty() }
                require(text == null || text.length <= 200) { "Odpowiedź może mieć do 200 znaków." }
                val blank = owners.isEmpty() && text == null
                if (cmd.type == "draft") {
                    if (blank) drafts.remove(id) else drafts[id] = Guess(owners, text, ROUND_MS)
                    return false
                }
                require(!blank) { "Podaj odpowiedź." }
                answers[id] = Guess(owners, text, ROUND_MS - (deadline - now))
                drafts.remove(id)
                advanceIfEveryone(now)
            }
            "ready" -> {
                require(phase == Phase.RESULTS && cmd.roundNumber == roundNumber) { "Gotowość dotyczy zakończonej rundy." }
                ready.add(id)
                advanceIfEveryone(now)
            }
            else -> throw IllegalArgumentException("Nieznane polecenie.")
        }
        return true
    }

    fun withdraw(seat: Seat, now: Long) {
        seat.token = null
        seat.peer = null
        seat.disconnectedAt = null
        val id = seat.player.id
        if (phase == Phase.LOBBY) {
            seats.remove(id)
            if (seats.isNotEmpty()) fitSettingsToRoster()
        }
        if (empty) return
        if (hostId == id) hostId = seats.values.first { it.token != null }.player.id
        drafts.remove(id)
        advanceIfEveryone(now)
    }

    private fun fitSettingsToRoster() {
        if (maxRounds == 0) mode = "OWNERS"
        roundCount = roundCount.coerceAtMost(maxRounds)
    }

    private fun advanceIfEveryone(now: Long) {
        val active = seats.filterValues { it.token != null }.keys
        when {
            phase == Phase.ROUND && answers.keys.containsAll(active) -> finishRound(now)
            phase == Phase.RESULTS && ready.containsAll(active) -> nextRound(now)
        }
    }

    fun advance(now: Long): Boolean {
        var changed = false
        while (timed && now >= deadline) {
            when (phase) {
                Phase.COUNTDOWN -> {
                    phase = Phase.ROUND
                    deadline += ROUND_MS
                }
                Phase.ROUND -> {
                    answers.putAll(drafts)
                    finishRound(deadline)
                }
                Phase.RESULTS -> nextRound(deadline)
                else -> error("Unexpected timed phase")
            }
            changed = true
        }
        return changed
    }

    private fun clearRound() {
        drafts.clear()
        answers.clear()
        ready.clear()
        result = null
    }

    private fun nextRound(now: Long) {
        clearRound()
        roundNumber++
        phase = Phase.COUNTDOWN
        deadline = now + COUNTDOWN_MS
    }

    private fun finishRound(now: Long) {
        val graded = seats.keys.map { id ->
            val guess = answers[id]
            val grade = guess?.let { RoundScoring.grade(mode, track, seats.keys, it.owners, it.text, it.elapsed) }
            val answer = NetAnswer(id, guess?.owners.orEmpty().toList(), guess?.text, guess?.elapsed,
                grade?.correct ?: false, grade?.accuracy ?: 0.0, grade?.points ?: 0)
            val previous = totals[id]
            totals[id] = NetRank(id, (previous?.points ?: 0) + answer.points,
                (previous?.correctAnswers ?: 0) + if (answer.correct) 1 else 0)
            answer
        }
        result = NetResult(track.ownerIds.toList(), track.title, track.artist, graded)
        phase = if (roundNumber == tracks.size) Phase.FINISHED else Phase.RESULTS
        deadline = now + RESULTS_MS
    }

    fun state(now: Long) = RoomState(
        code = code, hostId = hostId, players = seats.values.map { it.player },
        capacity = capacity, roundCount = roundCount, maxRounds = maxRounds,
        showOwnerCount = showOwnerCount, mode = mode, phase = phase.name, roundNumber = roundNumber,
        remainingMs = if (timed) (deadline - now).coerceAtLeast(0) else 0,
        audioKey = if (phase == Phase.COUNTDOWN || phase == Phase.ROUND) track.key else null,
        ownerCountHint = if (mode == "OWNERS" && showOwnerCount && phase == Phase.ROUND) track.ownerIds.size else null,
        answeredIds = answers.keys.toList(), readyIds = ready.toList(),
        ranking = seats.keys.map { totals[it] ?: NetRank(it, 0, 0) }
            .sortedWith(compareByDescending<NetRank> { it.points }.thenByDescending { it.correctAnswers }),
        result = result,
    )
}
