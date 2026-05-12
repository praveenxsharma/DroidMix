package com.droidmix

import kotlin.math.*

class DspChain(private val sampleRate: Double = 48000.0) {
    private val lowShelf = BiquadFilter()
    private val midPeak = BiquadFilter()
    private val highShelf = BiquadFilter()

    var eqLowGainDb: Double = 0.0
        set(value) {
            field = value
            rebuildLow()
        }

    var eqMidGainDb: Double = 0.0
        set(value) {
            field = value
            rebuildMid()
        }

    var eqHighGainDb: Double = 0.0
        set(value) {
            field = value
            rebuildHigh()
        }

    var compThresholdDb: Double = -20.0
    var compRatio: Double = 4.0
    var compAttackMs: Double = 5.0
    var compReleaseMs: Double = 80.0
    var compMakeupGainDb: Double = 6.0

    var gateThresholdDb: Double = -50.0
    var gateHoldMs: Double = 80.0

    var inputGainDb: Double = 0.0

    private var envDb: Double = -100.0
    private var gateHoldSamples: Int = 0
    private var gateOpen: Boolean = false

    init {
        rebuildLow()
        rebuildMid()
        rebuildHigh()
    }

    private fun rebuildLow() {
        if (eqLowGainDb != 0.0) {
            lowShelf.setLowShelf(120.0, eqLowGainDb, sampleRate)
        } else {
            lowShelf.setBypass()
        }
    }

    private fun rebuildMid() {
        if (eqMidGainDb != 0.0) {
            midPeak.setPeak(1000.0, eqMidGainDb, 0.9, sampleRate)
        } else {
            midPeak.setBypass()
        }
    }

    private fun rebuildHigh() {
        if (eqHighGainDb != 0.0) {
            highShelf.setHighShelf(8000.0, eqHighGainDb, sampleRate)
        } else {
            highShelf.setBypass()
        }
    }

    fun processBlock(buf: ShortArray) {
        val attackCoef = exp(-1.0 / (sampleRate * compAttackMs / 1000.0))
        val releaseCoef = exp(-1.0 / (sampleRate * compReleaseMs / 1000.0))
        val inputGainLin = 10.0.pow(inputGainDb / 20.0)
        val makeupLin = 10.0.pow(compMakeupGainDb / 20.0)
        val holdSamples = (sampleRate * gateHoldMs / 1000.0).toInt()

        for (i in buf.indices) {
            var x = buf[i].toDouble() / 32768.0 * inputGainLin

            // Noise Gate
            val xDbGate = if (x == 0.0) -100.0 else 20.0 * log10(abs(x))
            if (xDbGate > gateThresholdDb) {
                gateOpen = true
                gateHoldSamples = holdSamples
            } else {
                if (gateHoldSamples > 0) {
                    gateHoldSamples--
                } else {
                    gateOpen = false
                }
            }
            if (!gateOpen) {
                x = 0.0
            }

            // EQ
            x = lowShelf.process(x)
            x = midPeak.process(x)
            x = highShelf.process(x)

            // Compressor
            val xAbsDb = if (x == 0.0) -100.0 else 20.0 * log10(abs(x))
            val coef = if (xAbsDb > envDb) attackCoef else releaseCoef
            envDb = coef * envDb + (1.0 - coef) * xAbsDb

            val gainReductionDb = if (envDb > compThresholdDb) {
                (compThresholdDb - envDb) * (1.0 - 1.0 / compRatio)
            } else {
                0.0
            }

            val compGain = 10.0.pow(gainReductionDb / 20.0) * makeupLin
            x *= compGain

            buf[i] = (x.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
        }
    }

    fun reset() {
        lowShelf.reset()
        midPeak.reset()
        highShelf.reset()
        envDb = -100.0
        gateHoldSamples = 0
        gateOpen = false
    }
}
