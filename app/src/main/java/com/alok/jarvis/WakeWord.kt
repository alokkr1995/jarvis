package com.alok.jarvis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.nio.FloatBuffer

/**
 * Free, offline "Hey Jarvis" wake word (openWakeWord models, no account / key needed).
 * Pipeline per 80 ms of 16 kHz audio: melspectrogram → speech embedding → wake-word classifier.
 */
class WakeWord(context: Context, private val threshold: Float, private val onWake: () -> Unit) {
    private val env = OrtEnvironment.getEnvironment()
    private val mel = load(context, "melspectrogram.onnx")
    private val emb = load(context, "embedding_model.onnx")
    private val ww = load(context, "hey_jarvis.onnx")

    @Volatile private var running = false
    private var thread: Thread? = null

    private fun load(c: Context, file: String): OrtSession =
        env.createSession(c.assets.open(file).use { it.readBytes() }, OrtSession.SessionOptions())

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
        try { mel.close(); emb.close(); ww.close() } catch (_: Exception) {}
    }

    private fun run1(s: OrtSession, data: FloatArray, shape: LongArray): FloatArray {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).use { t ->
            s.run(mapOf(s.inputNames.first() to t)).use { r ->
                val fb = (r.get(0) as OnnxTensor).floatBuffer
                return FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val sr = 16000
        val chunk = 1280                       // 80 ms
        val keep = 480                         // 3 extra mel hops of context
        val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sr, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, chunk * 8))
        } catch (e: Exception) { JarvisService.log("Mic error: ${e.message}"); running = false; return }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            JarvisService.log("Mic is busy"); rec.release(); running = false; return
        }

        val pcm = ShortArray(chunk)
        val raw = FloatArray(chunk + keep)
        var rawLen = 0
        val melBuf = ArrayDeque<FloatArray>()
        repeat(76) { melBuf.addLast(FloatArray(32) { 1f }) }
        val feats = ArrayDeque<FloatArray>()
        val melFlat = FloatArray(76 * 32)
        val featFlat = FloatArray(16 * 96)

        try {
            rec.startRecording()
            while (running) {
                var got = 0
                while (got < chunk && running) {
                    val n = rec.read(pcm, got, chunk - got)
                    if (n < 0) { JarvisService.log("Mic read error $n"); running = false; break }
                    got += n
                }
                if (got < chunk) break

                System.arraycopy(raw, chunk, raw, 0, keep)
                for (i in 0 until chunk) raw[keep + i] = pcm[i].toFloat()
                rawLen = minOf(raw.size, rawLen + chunk)
                val input = raw.copyOfRange(raw.size - rawLen, raw.size)

                val m = run1(mel, input, longArrayOf(1, input.size.toLong()))
                for (f in 0 until m.size / 32) melBuf.addLast(FloatArray(32) { m[f * 32 + it] / 10f + 2f })
                while (melBuf.size > 76) melBuf.removeFirst()
                melBuf.forEachIndexed { i, fr -> System.arraycopy(fr, 0, melFlat, i * 32, 32) }

                feats.addLast(run1(emb, melFlat, longArrayOf(1, 76, 32, 1)))
                while (feats.size > 16) feats.removeFirst()
                if (feats.size < 16) continue
                feats.forEachIndexed { i, v -> System.arraycopy(v, 0, featFlat, i * 96, 96) }

                val score = run1(ww, featFlat, longArrayOf(1, 16, 96))[0]
                if (score >= threshold) {
                    running = false
                    JarvisService.log("Wake word (score ${"%.2f".format(score)})")
                    onWake()
                }
            }
        } catch (e: Exception) {
            JarvisService.log("Wake word error: ${e.message}")
            running = false
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
        }
    }
}
