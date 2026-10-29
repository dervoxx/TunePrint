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

/** Retains a local game and its audio across configuration changes. */
class GameController(application: Application) : AndroidViewModel(application) {
    var nickname by mutableStateOf("Ty")
        private set
    private var session = newSession()
    var snapshot by mutableStateOf(session.snapshot)
        private set
    var audioLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
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

    fun updateNickname(value: String) { nickname = value.take(20) }

    fun start() {
        session = newSession()
        session.start()
        error = null
        lastTick = SystemClock.elapsedRealtime()
        publish()
    }

    fun answer(ownerId: String) {
        if (!foreground || audioLoading || error != null) return
        advanceToNow()
        if (audioLoading || error != null) return
        session.answer(ownerId)
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
        audioLoading = false
        error = null
        session = newSession()
        snapshot = session.snapshot
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
    }

    private fun newSession() = GameSession(
        tracks = RoundTracks.all.shuffled().map {
            GameTrack(it.assetName, it.title, it.artist, it.ownerId)
        },
        localName = nickname,
    )

    private fun advanceToNow() {
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastTick
        lastTick = now
        if (!foreground || audioLoading || error != null) return
        session.advance(delta.coerceAtLeast(0))
        publish()
    }

    private fun publish() {
        val oldPhase = snapshot.phase
        snapshot = session.snapshot
        if (snapshot.phase == oldPhase) return
        if (snapshot.phase == GamePhase.ROUND) beginAudio()
        else {
            audio.stop()
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
