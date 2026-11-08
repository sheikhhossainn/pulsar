package bd.pulsar

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** The worker owns every native object. UI cancellation only sets a flag. */
internal class Voice(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var engine: OnlineRecognizer? = null
    private var active: AtomicBoolean? = null
    private var closed = false
    private val evict = Runnable {
        if (!closed && active == null)
            worker.execute {
                engine?.release()
                engine = null
            }
    }

    fun stop() {
        active?.set(false)
        active = null
        ui.removeCallbacks(evict)
        if (!closed) ui.postDelayed(evict, 30_000)
    }

    fun close() {
        closed = true
        stop()
        worker.execute {
            engine?.release()
            engine = null
        }
        worker.shutdown()
    }

    @SuppressLint("MissingPermission")
    fun listen(
        onReady: () -> Unit,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: () -> Unit,
    ) {
        if (closed) return
        stop()
        ui.removeCallbacks(evict)
        val live = AtomicBoolean(true)
        active = live
        fun deliver(block: () -> Unit) {
            ui.post { if (live.get() && !closed) block() }
        }
        worker.execute {
            var record: AudioRecord? = null
            var stream: OnlineStream? = null
            try {
                val recognizer =
                    engine
                        ?: OnlineRecognizer(
                                context.assets,
                                OnlineRecognizerConfig(
                                    modelConfig =
                                        OnlineModelConfig(
                                            transducer =
                                                OnlineTransducerModelConfig(
                                                    "asr/encoder.onnx",
                                                    "asr/decoder.onnx",
                                                    "asr/joiner.onnx",
                                                ),
                                            tokens = "asr/tokens.txt",
                                            numThreads = 2,
                                        ),
                                    endpointConfig =
                                        EndpointConfig(
                                            rule1 = EndpointRule(false, 5f, 0f),
                                            rule2 = EndpointRule(true, 0.85f, 0f),
                                        ),
                                ),
                            )
                            .also { engine = it }
                if (!live.get()) return@execute
                val min =
                    AudioRecord.getMinBufferSize(
                        16000,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                require(min > 0)
                record =
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        16000,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(4096, min * 2),
                    )
                check(record.state == AudioRecord.STATE_INITIALIZED)
                stream = recognizer.createStream()
                val buffer = ShortArray(1600)
                record.startRecording()
                check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                deliver(onReady)
                val start = SystemClock.elapsedRealtime()
                var partial = ""
                while (live.get() && SystemClock.elapsedRealtime() - start < 18_000) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n < 0) error("audio read")
                    if (n == 0) continue
                    stream.acceptWaveform(FloatArray(n) { buffer[it] / 32768f }, 16000)
                    while (recognizer.isReady(stream)) recognizer.decode(stream)
                    val text = recognizer.getResult(stream).text.trim()
                    if (text != partial) {
                        partial = text
                        deliver { onPartial(text) }
                    }
                    if (recognizer.isEndpoint(stream)) break
                }
                if (live.get()) {
                    stream.acceptWaveform(FloatArray(6400), 16000)
                    stream.inputFinished()
                    while (recognizer.isReady(stream)) recognizer.decode(stream)
                    val final = recognizer.getResult(stream).text.trim()
                    deliver {
                        stop()
                        onResult(final)
                    }
                }
            } catch (e: Exception) {
                deliver {
                    stop()
                    onError()
                }
            } catch (e: LinkageError) {
                deliver {
                    stop()
                    onError()
                }
            } finally {
                record?.let {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                        runCatching { it.stop() }
                    it.release()
                }
                stream?.release()
            }
        }
    }
}
