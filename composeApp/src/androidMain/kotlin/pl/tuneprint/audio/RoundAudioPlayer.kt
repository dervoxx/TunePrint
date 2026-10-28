package pl.tuneprint.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/** All methods and callbacks run on the main thread. The owner controls the round duration. */
class RoundAudioPlayer(context: Context) {
    private val assets = context.applicationContext.assets
    private var player: MediaPlayer? = null
    // A lifecycle gate, deliberately kept across stop/play while the Activity is in background.
    private var paused = false
    private var ready = false
    private var started = false
    private var onStarted: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    fun play(
        assetName: String,
        startAtMs: Int = 30_000,
        onStarted: () -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()
        this.onStarted = onStarted
        this.onError = onError
        val current = MediaPlayer()
        player = current
        try {
            current.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            current.setOnErrorListener { failed, _, _ ->
                if (player === failed) fail("Nie udało się odtworzyć nagrania. Spróbuj ponownie.")
                true
            }
            current.setOnPreparedListener { prepared ->
                if (player !== prepared) return@setOnPreparedListener
                if (prepared.duration <= 0) {
                    fail("Nagranie jest puste lub uszkodzone. Sprawdź pliki w folderze songs.")
                    return@setOnPreparedListener
                }
                prepared.isLooping = prepared.duration < 15_000
                // Keep a full round available even when a supplied clip is shorter than a song.
                val offset = startAtMs.coerceIn(0, (prepared.duration - 15_000).coerceAtLeast(0))
                if (offset == 0) {
                    ready = true
                    startIfReady()
                } else {
                    prepared.setOnSeekCompleteListener { sought ->
                        if (player === sought) {
                            sought.setOnSeekCompleteListener(null)
                            ready = true
                            startIfReady()
                        }
                    }
                    @Suppress("DEPRECATION")
                    prepared.seekTo(offset)
                }
            }
            assets.openFd(assetName).use { descriptor ->
                current.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
            current.prepareAsync()
        } catch (_: Exception) {
            fail("Brak nagrania lub nieprawidłowy plik. Sprawdź pliki w folderze songs i zbuduj aplikację ponownie.")
        }
    }

    fun pause() {
        paused = true
        if (ready && started) {
            try {
                player?.pause()
            } catch (_: IllegalStateException) {
                fail("Odtwarzanie zostało przerwane. Spróbuj ponownie.")
            }
        }
    }

    fun resume() {
        paused = false
        startIfReady()
    }

    fun stop() {
        val previous = player
        player = null
        ready = false
        started = false
        onStarted = null
        onError = null
        previous?.setOnPreparedListener(null)
        previous?.setOnSeekCompleteListener(null)
        previous?.setOnErrorListener(null)
        previous?.release()
    }

    fun release() = stop()

    private fun startIfReady() {
        val current = player ?: return
        if (!ready || paused) return
        try {
            current.start()
            if (!started) {
                started = true
                onStarted?.invoke()
            }
        } catch (_: IllegalStateException) {
            fail("Nie udało się uruchomić nagrania. Spróbuj ponownie.")
        }
    }

    private fun fail(message: String) {
        val callback = onError
        stop()
        callback?.invoke(message)
    }
}
