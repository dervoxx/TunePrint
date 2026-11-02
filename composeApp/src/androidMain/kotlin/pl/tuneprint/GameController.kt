package pl.tuneprint

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import pl.tuneprint.audio.RoundAudioPlayer
import pl.tuneprint.audio.RoundTracks
import pl.tuneprint.game.GamePhase
import pl.tuneprint.game.GameSession
import pl.tuneprint.game.GameTrack
import pl.tuneprint.game.RoomLobby
import pl.tuneprint.game.RoomRoute
import pl.tuneprint.game.Player
import pl.tuneprint.game.selectRoundTracks

/** Retains a local game and its audio across configuration changes. */
class GameController(application: Application) : AndroidViewModel(application) {
    var nickname by mutableStateOf("Olek")
        private set
    private var session = newSession()
    private var room = newRoom()
    var roomSnapshot by mutableStateOf(room.snapshot)
        private set
    var snapshot by mutableStateOf(session.snapshot)
        private set
    var audioLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var audioLevels by mutableStateOf(emptyList<Float>())
        private set

    private val audio = RoundAudioPlayer(application)
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private var lastTick = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (!foreground) return
            advanceToNow()
            handler.postDelayed(this, 25)
        }
    }

    fun updateNickname(value: String) {
        if (roomSnapshot.route == RoomRoute.HOME) nickname = value.take(20)
    }

    private fun newRoom() = RoomLobby(
        players = session.players.sortedBy {
            when (it.id) {
                "you" -> 0
                "kacper" -> 1
                "ziobro" -> 2
                else -> 3
            }
        },
        maxRounds = RoundTracks.all.size,
    )

    private fun resetEntry() {
        session = newSession()
        snapshot = session.snapshot
        room = newRoom()
        roomSnapshot = room.snapshot
        lastTick = SystemClock.elapsedRealtime()
    }

    fun createRoom() {
        if (nickname.isBlank() || roomSnapshot.route != RoomRoute.HOME) return
        resetEntry()
        advanceToNow()
        room.create()
        roomSnapshot = room.snapshot
    }

    fun openJoin() {
        if (nickname.isBlank() || roomSnapshot.route != RoomRoute.HOME) return
        resetEntry()
        room.openJoin()
        roomSnapshot = room.snapshot
    }

    fun updateRoomCode(value: String) {
        room.editCode(value)
        roomSnapshot = room.snapshot
    }

    fun selectRoom(code: String) {
        room.editCode(code)
        joinRoom()
    }

    fun joinRoom() {
        room.join()
        roomSnapshot = room.snapshot
        lastTick = SystemClock.elapsedRealtime()
    }

    fun leaveRoom() {
        audio.stop()
        audioLevels = emptyList()
        audioLoading = false
        error = null
        session = newSession()
        snapshot = session.snapshot
        room.leave()
        roomSnapshot = room.snapshot
        lastTick = SystemClock.elapsedRealtime()
    }

    val availableRoundCount: Int
        get() = playableTracks(roomSnapshot.players).size.coerceAtLeast(1)

    fun updateCapacity(value: Int) {
        room.setCapacity(value)
        roomSnapshot = room.snapshot
        room.setRoundCount(roomSnapshot.roundCount.coerceAtMost(availableRoundCount))
        roomSnapshot = room.snapshot
    }

    fun updateShowOwnerCount(value: Boolean) {
        room.setShowOwnerCount(value)
        roomSnapshot = room.snapshot
    }

    fun updateRoundCount(value: Int) {
        room.setRoundCount(value.coerceIn(1, availableRoundCount))
        roomSnapshot = room.snapshot
    }

    fun start() {
        if (!roomSnapshot.canStart || snapshot.phase != GamePhase.LOBBY) return
        val participants = roomSnapshot.players
        if (roomSnapshot.roundCount > playableTracks(participants).size) return
        room.start()
        roomSnapshot = room.snapshot
        session = newSession(roomSnapshot.roundCount, participants, roomSnapshot.showOwnerCount)
        session.start()
        error = null
        lastTick = SystemClock.elapsedRealtime()
        publish()
    }

    fun toggleOwner(ownerId: String) {
        if (!foreground || audioLoading || error != null) return
        advanceToNow()
        if (audioLoading || error != null) return
        session.toggleOwner(ownerId)
        publish()
    }

    fun submitAnswer() {
        if (!foreground || audioLoading || error != null) return
        advanceToNow()
        session.submitAnswer()
        publish()
    }

    fun ready() {
        if (!foreground) return
        advanceToNow()
        session.ready()
        publish()
    }

    fun replay() {
        audio.stop()
        audioLevels = emptyList()
        audioLoading = false
        error = null
        session = newSession()
        snapshot = session.snapshot
        room.replay()
        roomSnapshot = room.snapshot
        lastTick = SystemClock.elapsedRealtime()
    }

    fun retryAudio() {
        error = null
        beginAudio()
    }

    fun resume() {
        if (foreground) return
        foreground = true
        lastTick = SystemClock.elapsedRealtime()
        audio.resume()
        handler.post(tick)
    }

    fun pause() {
        if (!foreground) return
        advanceToNow()
        foreground = false
        handler.removeCallbacks(tick)
        audio.pause()
        audioLevels = emptyList()
    }

    private fun playableTracks(participants: List<Player>? = null): List<GameTrack> {
        val ids = participants?.map { it.id }?.toSet()
        return RoundTracks.all.mapNotNull { track ->
            val owners = if (ids == null) track.ownerIds else track.ownerIds.intersect(ids)
            if (owners.isEmpty()) null else GameTrack(track.assetName, track.title, track.artist, owners)
        }
    }

    private fun newSession(roundCount: Int = 3, participants: List<Player>? = null, showOwnerCount: Boolean = false) = GameSession(
        tracks = selectRoundTracks(
            playableTracks(participants), roundCount, "Halo halo z aresztu funny.webm",
        ),
        localName = nickname,
        participants = participants,
        showOwnerCount = showOwnerCount,
    )

    private fun advanceToNow() {
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastTick
        lastTick = now
        if (!foreground) return
        room.advance(delta.coerceAtLeast(0))
        roomSnapshot = room.snapshot
        if (audioLoading || error != null || roomSnapshot.route != RoomRoute.GAME) return
        session.advance(delta.coerceAtLeast(0))
        publish()
        audioLevels = if (snapshot.phase == GamePhase.ROUND && !audioLoading) audio.levels() else emptyList()
    }

    private fun publish() {
        val oldPhase = snapshot.phase
        snapshot = session.snapshot
        if (snapshot.phase == oldPhase) return
        if (snapshot.phase == GamePhase.ROUND) beginAudio()
        else {
            audio.stop()
            audioLevels = emptyList()
            audioLoading = false
        }
    }

    private fun beginAudio() {
        val key = snapshot.audioKey ?: return
        val track = RoundTracks.all.first { it.assetName == key }
        audioLoading = true
        audio.play(track.assetName, track.startAtMs,
            onStarted = {
                audioLoading = false
                lastTick = SystemClock.elapsedRealtime()
            },
            onError = {
                audioLoading = false
                error = it
            },
        )
        if (!foreground) audio.pause()
    }

    override fun onCleared() {
        handler.removeCallbacksAndMessages(null)
        audio.release()
    }
}
