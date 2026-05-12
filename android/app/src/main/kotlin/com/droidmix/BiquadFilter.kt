package com.droidmix

import kotlin.math.*

class BiquadFilter {
    private var b0: Double = 1.0
    private var b1: Double = 0.0
    private var b2: Double = 0.0
    private var a1: Double = 0.0
    private var a2: Double = 0.0
    
    private var s1: Double = 0.0
    private var s2: Double = 0.0

    fun setLowShelf(fc: Double, gainDb: Double, sampleRate: Double) {
        val A = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / sampleRate
        val alpha = sin(w0) / sqrt(2.0)
        val sqA = sqrt(A)

        val b0_raw = A * ((A + 1) - (A - 1) * cos(w0) + 2 * sqA * alpha)
        val b1_raw = 2 * A * ((A - 1) - (A + 1) * cos(w0))
        val b2_raw = A * ((A + 1) - (A - 1) * cos(w0) - 2 * sqA * alpha)
        val a0_raw = (A + 1) + (A - 1) * cos(w0) + 2 * sqA * alpha
        val a1_raw = -2 * ((A - 1) + (A + 1) * cos(w0))
        val a2_raw = (A + 1) + (A - 1) * cos(w0) - 2 * sqA * alpha

        b0 = b0_raw / a0_raw
        b1 = b1_raw / a0_raw
        b2 = b2_raw / a0_raw
        a1 = a1_raw / a0_raw
        a2 = a2_raw / a0_raw
    }

    fun setPeak(fc: Double, gainDb: Double, q: Double, sampleRate: Double) {
        val A = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val a0_raw = 1 + alpha / A

        b0 = (1 + alpha * A) / a0_raw
        b1 = (-2 * cos(w0)) / a0_raw
        b2 = (1 - alpha * A) / a0_raw
        a1 = (-2 * cos(w0)) / a0_raw
        a2 = (1 - alpha / A) / a0_raw
    }

    fun setHighShelf(fc: Double, gainDb: Double, sampleRate: Double) {
        val A = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / sampleRate
        val alpha = sin(w0) / sqrt(2.0)
        val sqA = sqrt(A)
        val a0_raw = (A + 1) - (A - 1) * cos(w0) + 2 * sqA * alpha

        b0 = A * ((A + 1) + (A - 1) * cos(w0) + 2 * sqA * alpha) / a0_raw
        b1 = -2 * A * ((A - 1) + (A + 1) * cos(w0)) / a0_raw
        b2 = A * ((A + 1) + (A - 1) * cos(w0) - 2 * sqA * alpha) / a0_raw
        a1 = 2 * ((A - 1) - (A + 1) * cos(w0)) / a0_raw
        a2 = ((A + 1) - (A - 1) * cos(w0) - 2 * sqA * alpha) / a0_raw
    }

    fun setBypass() {
        b0 = 1.0
        b1 = 0.0
        b2 = 0.0
        a1 = 0.0
        a2 = 0.0
    }

    fun reset() {
        s1 = 0.0
        s2 = 0.0
    }

    fun process(x: Double): Double {
        val y = b0 * x + s1
        s1 = b1 * x - a1 * y + s2
        s2 = b2 * x - a2 * y
        return y
    }

    fun processBuffer(buf: ShortArray, offset: Int = 0, length: Int = buf.size - offset) {
        for (i in offset until offset + length) {
            val x = buf[i].toDouble() / 32768.0
            val y = process(x)
            buf[i] = (y.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
        }
    }
}
