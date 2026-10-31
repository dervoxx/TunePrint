package pl.tuneprint.audio

import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.sqrt

/** Decodes only the played fragment; no microphone or output-capture permission is needed. */
internal object AudioEnvelope {
    const val BIN_MS = 40
    private const val DURATION_MS = 15_000

    fun decode(assets: AssetManager, assetName: String, startMs: Int, cancelled: () -> Boolean): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            assets.openFd(assetName).use {
                extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return floatArrayOf()
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return floatArrayOf()
            val startUs = startMs * 1_000L
            val endUs = startUs + DURATION_MS * 1_000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val sums = DoubleArray(DURATION_MS / BIN_MS + 1)
            val counts = IntArray(sums.size)
            val info = MediaCodec.BufferInfo()
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var inputEnded = false
            var outputEnded = false
            val deadline = SystemClock.elapsedRealtime() + 20_000

            while (!outputEnded && !cancelled() && !Thread.currentThread().isInterrupted &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(5_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex) ?: break
                        val sampleTime = extractor.sampleTime
                        val size = if (sampleTime < 0 || sampleTime >= endUs) -1 else extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, endUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, 5_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = decoder.outputFormat
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                    }
                    else -> if (outputIndex >= 0) {
                        try {
                            val output = decoder.getOutputBuffer(outputIndex)
                            if (output != null && info.size > 0 && channels > 0 && sampleRate > 0) {
                                val bytes = output.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                                bytes.position(info.offset)
                                bytes.limit(info.offset + info.size)
                                val floatPcm = encoding == AudioFormat.ENCODING_PCM_FLOAT
                                val sampleBytes = if (floatPcm) 4 else 2
                                if (encoding == AudioFormat.ENCODING_PCM_16BIT || floatPcm) {
                                    var sample = 0
                                    while (bytes.remaining() >= sampleBytes) {
                                        val value = if (floatPcm) bytes.float.toDouble() else bytes.short / 32768.0
                                        val relativeUs = info.presentationTimeUs - startUs +
                                            (sample / channels) * 1_000_000L / sampleRate
                                        if (relativeUs >= 0) {
                                            val bin = (relativeUs / (BIN_MS * 1_000L)).toInt()
                                            if (bin in sums.indices && value.isFinite()) {
                                                sums[bin] += value * value
                                                counts[bin]++
                                            }
                                        }
                                        sample++
                                    }
                                }
                            }
                        } finally {
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
            if (cancelled() || Thread.currentThread().isInterrupted) return floatArrayOf()
            val rms = FloatArray(sums.size) { i ->
                if (counts[i] == 0) 0f else sqrt(sums[i] / counts[i]).toFloat()
            }
            val peak = rms.maxOrNull()?.coerceAtLeast(0.001f) ?: 1f
            return FloatArray(rms.size) { i -> (rms[i] / peak).coerceIn(0f, 1f).pow(1.6f) }
        } finally {
            // release() is valid from both configured and executing codec states.
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}
