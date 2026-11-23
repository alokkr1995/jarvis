package com.alok.jarvis

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/**
 * Free, offline wake word "Jarvis" (sherpa-onnx keyword spotter, runs fully on the phone,
 * no account or key; audio never leaves the device).
 */
class WakeWord(context: Context, sensitivity: Float, private val onWake: () -> Unit) {
    private val spotter: KeywordSpotter = KeywordSpotter(
        context.assets,
        KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "kws/encoder.onnx",
                    decoder = "kws/decoder.onnx",
                    joiner = "kws/joiner.onnx",
                ),
                tokens = "kws/tokens.txt",
                numThreads = 1,
                modelType = "zipformer2",
            ),
            keywordsFile = "kws/keywords.txt",
            keywordsScore = 2.0f,
            // sensitivity 0.5 → threshold 0.20 ; higher sensitivity = lower threshold
            keywordsThreshold = (0.45f - 0.5f * sensitivity).coerceIn(0.05f, 0.40f),
        )
    )

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "jarvis-wakeword").also { it.start() }
    }

    fun stop() {
        running = false
        val t = thread
        if (t != null && t != Thread.currentThread()) try { t.join(800) } catch (_: Exception) {}
        thread = null
    }

    fun close() {
        stop()
        try { spotter.release() } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val sr = 16000
        val chunk = 1600                       // 100 ms
        val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sr, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, chunk * 8))
        } catch (e: Exception) { JarvisService.log("Mic error: ${e.message}"); running = false; return }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            JarvisService.log("Mic is busy"); rec.release(); running = false; return
        }
        val stream = spotter.createStream()
        val pcm = ShortArray(chunk)
        val samples = FloatArray(chunk)
        try {
            rec.startRecording()
            while (running) {
                val n = rec.read(pcm, 0, chunk)
                if (n < 0) { JarvisService.log("Mic read error $n"); break }
                if (n == 0) continue
                for (i in 0 until n) samples[i] = pcm[i] / 32768f
                stream.acceptWaveform(if (n == chunk) samples else samples.copyOf(n), sr)
                while (running && spotter.isReady(stream)) {
                    spotter.decode(stream)
                    val kw = spotter.getResult(stream).keyword
                    if (kw.isNotBlank()) {
                        spotter.reset(stream)
                        running = false
                        JarvisService.log("Wake word: $kw")
                        onWake()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            JarvisService.log("Wake word error: ${e.message}")
        } finally {
            running = false
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
            try { stream.release() } catch (_: Exception) {}
        }
    }
}
