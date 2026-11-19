package pl.tuneprint.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

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
    private val analysisWorker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "TunePrint audio envelope").apply { isDaemon = true }
    }
    private val generation = AtomicInteger()
    private var analysis: Future<*>? = null
    @Volatile private var envelope: Pair<Int, FloatArray>? = null
    private var segmentStartMs = 0

    fun play(
        assetName: String,
        startAtMs: Int = 30_000,
        onStarted: () -> Unit,
        onError: (String) -> Unit,
        elapsedInRoundMs: () -> Long = { 0L },
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
                val base = startAtMs.coerceIn(0, (prepared.duration - 15_000).coerceAtLeast(0))
                val offset = ((base + elapsedInRoundMs().coerceIn(0, 14_999)) % prepared.duration).toInt()
                segmentStartMs = base
                analyze(assetName, base)
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
        generation.incrementAndGet()
        analysis?.cancel(true)
        analysis = null
        envelope = null
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

    fun release() {
        stop()
        analysisWorker.shutdownNow()
    }

    /** Recent audio energy, oldest to newest. Call on the main thread, like other player methods. */
    fun levels(): List<Float> {
        val current = player ?: return List(11) { 0f }
        val measured = envelope ?: return List(11) { 0f }
        val bins = measured.second
        if (!ready || !started || paused || measured.first != generation.get() || bins.isEmpty()) {
            return List(11) { 0f }
        }
        val position = try {
            current.currentPosition - segmentStartMs
        } catch (_: IllegalStateException) {
            return List(11) { 0f }
        }
        val index = position / AudioEnvelope.BIN_MS
        return List(11) { bar -> bins.getOrNull(index - 10 + bar) ?: 0f }
    }

    private fun analyze(assetName: String, offset: Int) {
        if (analysisWorker.isShutdown) return
        val request = generation.get()
        analysis = analysisWorker.submit {
            val result = runCatching {
                AudioEnvelope.decode(assets, assetName, offset) { request != generation.get() }
            }.getOrElse { floatArrayOf() }
            if (request == generation.get() && !Thread.currentThread().isInterrupted) envelope = request to result
        }
    }

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
