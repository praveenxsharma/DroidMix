package com.droidmix

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

object AudioEngine {
    const val SAMPLE_RATE = 48000
    const val CHANNEL_CONF = AudioFormat.CHANNEL_IN_MONO
    const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private const val FRAMES = 512

    val dsp = DspChain(SAMPLE_RATE.toDouble())
    @Volatile var isRunning = false
        private set
    @Volatile var peakLinear = 0f
        private set

    private var engineJob: Job? = null

    fun start(host: String, port: Int, onError: (String) -> Unit) {
        if (isRunning) return
        isRunning = true
        dsp.reset()

        engineJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket(host, port)
                socket.tcpNoDelay = true
                val out = socket.getOutputStream()
                sendPcmStream(out)
            } catch (e: Exception) {
                if (isRunning) {
                    onError(e.message ?: "Unknown socket error")
                }
            } finally {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        engineJob?.cancel()
        engineJob = null
        peakLinear = 0f
    }

    @SuppressLint("MissingPermission")
    private fun sendPcmStream(out: OutputStream) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONF, ENCODING)
        val bufSize = max(minBuf * 4, FRAMES * 2 * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONF,
            ENCODING,
            bufSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        val pcmBuf = ShortArray(FRAMES)
        val byteBuf = ByteBuffer.allocate(FRAMES * 2).order(ByteOrder.LITTLE_ENDIAN)

        recorder.startRecording()

        try {
            while (isRunning) {
                val read = recorder.read(pcmBuf, 0, FRAMES, AudioRecord.READ_BLOCKING)
                if (read < 0) break

                dsp.processBlock(pcmBuf)

                var maxVal = 0
                for (j in 0 until read) {
                    val absVal = kotlin.math.abs(pcmBuf[j].toInt())
                    if (absVal > maxVal) {
                        maxVal = absVal
                    }
                }
                peakLinear = maxVal.toFloat() / 32768f

                byteBuf.clear()
                for (j in 0 until read) {
                    byteBuf.putShort(pcmBuf[j])
                }
                out.write(byteBuf.array(), 0, read * 2)
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }
}
