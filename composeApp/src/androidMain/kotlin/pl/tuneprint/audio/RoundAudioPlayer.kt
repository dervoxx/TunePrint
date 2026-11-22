package pl.tuneprint.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import pl.tuneprint.game.GameSession
import pl.tuneprint.net.isApplePreviewUrl

/** Owned by OnlineGame on the main thread. Network preparation and decoding run off the UI thread. */
internal class RoundAudioPlayer {
    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    @Volatile private var generation = 0L
    // Access only from worker. Stale platform callbacks may arrive after release.
    private var player: MediaPlayer? = null

    fun play(
        previewUrl: String,
        onStarted: () -> Unit,
        onError: (String) -> Unit,
        elapsedInRoundMs: () -> Long,
    ) {
        stop()
        if (!isApplePreviewUrl(previewUrl)) {
            onError("Serwer przekazał nieprawidłowy adres fragmentu Apple.")
            return
        }
        val request = generation
        val roundStart = SystemClock.elapsedRealtime() - elapsedInRoundMs().coerceAtLeast(0)
        fun elapsed() = SystemClock.elapsedRealtime() - roundStart
        val handler = worker ?: HandlerThread("TunePrint playback").let {
            it.start()
            thread = it
            Handler(it.looper).also { newHandler -> worker = newHandler }
        }
        handler.post {
            if (request != generation || elapsed() >= GameSession.ROUND_MS) return@post
            fun fail() {
                if (request != generation) return
                disposePlayer()
                main.post { if (request == generation) onError("Nie udało się odtworzyć fragmentu Apple. Spróbuj ponownie.") }
            }
            try {
                val current = MediaPlayer()
                player = current
                current.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                current.setOnErrorListener { failed, _, _ ->
                    if (player === failed) fail()
                    true
                }
                fun start() {
                    if (generation != request || player !== current || elapsed() >= GameSession.ROUND_MS) return
                    try {
                        current.start()
                        main.post { if (request == generation) onStarted() }
                    } catch (_: IllegalStateException) { fail() }
                }
                current.setOnPreparedListener { prepared ->
                    if (generation != request || player !== prepared) return@setOnPreparedListener
                    try {
                        val duration = prepared.duration
                        val position = previewOffset(elapsed(), duration) ?: return@setOnPreparedListener
                        prepared.isLooping = duration in 1 until GameSession.ROUND_MS.toInt()
                        if (position == 0) {
                            start()
                        } else {
                            prepared.setOnSeekCompleteListener {
                                if (player === prepared && generation == request) {
                                    prepared.setOnSeekCompleteListener(null)
                                    start()
                                }
                            }
                            if (Build.VERSION.SDK_INT >= 26) {
                                prepared.seekTo(position.toLong(), MediaPlayer.SEEK_CLOSEST)
                            } else {
                                @Suppress("DEPRECATION")
                                prepared.seekTo(position)
                            }
                        }
                    } catch (_: IllegalStateException) { fail() }
                }
                current.setDataSource(previewUrl)
                if (request == generation) current.prepareAsync()
            } catch (_: Exception) { fail() }
        }
    }

    fun stop() {
        generation++
        worker?.post { disposePlayer() }
    }

    fun release() {
        stop()
        thread?.quitSafely()
        thread = null
        worker = null
    }

    private fun disposePlayer() {
        val current = player ?: return
        player = null
        current.setOnPreparedListener(null)
        current.setOnSeekCompleteListener(null)
        current.setOnErrorListener(null)
        current.release()
    }
}

/** A short preview repeats within one round. Some streams report an unknown duration. */
internal fun previewOffset(elapsedMs: Long, durationMs: Int): Int? {
    if (elapsedMs >= GameSession.ROUND_MS) return null
    val elapsed = elapsedMs.coerceAtLeast(0).toInt()
    return if (durationMs > 0) elapsed % durationMs else elapsed
}
