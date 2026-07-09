package synthesizer

import kotlin.math.abs
import kotlin.math.tanh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelEffectTest {
    private fun squareChannel(vararg noteSpecs: Pair<String, Double>) =
            TestHelpers.channel(
                    waveGenerator = SquareWaveGenerator(),
                    notes = noteSpecs.map { (name, beats) -> TestHelpers.note(name, beats) }
            )

    @Test fun volumeScalesEverySample() {
        val base = squareChannel("C4" to 1.0)
        val plain = base.getSampleStream()
        val scaled = VolumeEffect(base, 0.3).getSampleStream()
        assertEquals(plain.size, scaled.size)
        for (i in plain.indices) {
            assertEquals(plain[i] * 0.3, scaled[i], 1e-10)
        }
    }

    @Test fun volumeZeroSilencesChannel() {
        val base = squareChannel("C4" to 1.0)
        val silent = VolumeEffect(base, 0.0).getSampleStream()
        assertTrue(silent.all { it == 0.0 })
    }

    @Test fun clipLimitsAmplitude() {
        val base = squareChannel("C4" to 1.0)
        val threshold = 0.3
        val clipped = ClipEffect(base, threshold).getSampleStream()
        assertTrue(clipped.all { abs(it) <= threshold + 1e-10 })
        assertTrue(clipped.any { abs(it) >= threshold - 1e-10 })
    }

    @Test fun clipLeavesQuietSignalUnchanged() {
        val base = TestHelpers.channel(notes = listOf(TestHelpers.note("C4", 0.01)))
        val quiet = VolumeEffect(base, 0.1).getSampleStream()
        val clipped = ClipEffect(VolumeEffect(base, 0.1), 0.5).getSampleStream()
        assertEquals(quiet.size, clipped.size)
        for (i in quiet.indices) {
            assertEquals(quiet[i], clipped[i], 1e-10)
        }
    }

    @Test fun tanhAppliesDrive() {
        val base = squareChannel("C4" to 1.0)
        val drive = 5.0
        val plain = base.getSampleStream()
        val distorted = TanhEffect(base, drive).getSampleStream()
        val idx = plain.indices.first { abs(plain[it]) > 0.5 }
        assertEquals(tanh(drive * plain[idx]), distorted[idx], 1e-10)
    }

    @Test fun tanhReducesSquareWavePeaks() {
        val base = squareChannel("C4" to 1.0)
        val distorted = TanhEffect(base, 3.0).getSampleStream()
        val plainPeak = base.getSampleStream().maxOf { abs(it) }
        val distortedPeak = distorted.maxOf { abs(it) }
        assertTrue(distortedPeak < plainPeak)
    }

    @Test fun adsAttackRampsFromSilence() {
        val base = squareChannel("C4" to 2.0)
        val enveloped = AttackDecaySustainEffect(base, attackEnd = 0.1, decayEnd = 0.5, sustain = 0.5)
                .getSampleStream()
        val early = enveloped.take(50).maxOf { abs(it) }
        val peak = enveloped.maxOf { abs(it) }
        assertTrue(early < peak, "attack should ramp amplitude up from zero")
    }

    @Test fun adsPluckDecaysToSilence() {
        val base = squareChannel("C4" to 2.0)
        val pluck = AttackDecaySustainEffect(base, attackEnd = 0.0, decayEnd = 0.15, sustain = 0.0)
                .getSampleStream()
        val head = pluck.take(200).maxOf { abs(it) }
        val tail = pluck.takeLast(200).maxOf { abs(it) }
        assertTrue(head > tail, "pluck envelope should decay toward silence")
    }

    @Test fun adsSustainHoldsLevelInTail() {
        val base = squareChannel("C4" to 2.0)
        val sustainLevel = 0.4
        val pad = AttackDecaySustainEffect(base, attackEnd = 0.05, decayEnd = 0.2, sustain = sustainLevel)
                .getSampleStream()
        val tailStart = (TestHelpers.SAMPLE_RATE * 0.5).toInt()
        val tail = pad.drop(tailStart).take(500)
        val expectedPeak = base.getSampleStream().maxOf { abs(it) } * sustainLevel
        assertTrue(tail.maxOf { abs(it) } in (expectedPeak * 0.8)..(expectedPeak * 1.2))
    }

    @Test fun effectsCanBeStacked() {
        val base = squareChannel("C4" to 1.0)
        val stacked = ClipEffect(TanhEffect(VolumeEffect(base, 0.5), 4.0), 0.4).getSampleStream()
        assertTrue(stacked.all { abs(it) <= 0.4 + 1e-10 })
        assertTrue(stacked.any { abs(it) > 0.0 })
    }

    @Test fun decoratorPreservesTotalSampleCount() {
        val base = squareChannel("C4" to 1.0, "D4" to 0.5)
        val plainLength = base.getSampleStream().size
        val decorated = VolumeEffect(AttackDecaySustainEffect(base, 0.01, 0.2, 0.5), 0.7)
        assertEquals(plainLength, decorated.getSampleStream().size)
    }
}
