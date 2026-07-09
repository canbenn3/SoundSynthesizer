package synthesizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaveGeneratorTest {
    @Test fun sineWaveAtZero() {
        assertEquals(0.0, SineWaveGenerator().generate(0.0), 1e-10)
    }

    @Test fun sineWaveAtQuarterPeriod() {
        assertEquals(1.0, SineWaveGenerator().generate(PI / 2), 1e-10)
    }

    @Test fun sineWaveAtHalfPeriod() {
        assertEquals(0.0, SineWaveGenerator().generate(PI), 1e-10)
    }

    @Test fun squareWaveIsHighInFirstHalf() {
        val gen = SquareWaveGenerator()
        assertEquals(1.0, gen.generate(0.0))
        assertEquals(1.0, gen.generate(PI / 2))
        assertEquals(-1.0, gen.generate(PI))
        assertEquals(-1.0, gen.generate(3 * PI / 2))
    }

    @Test fun sawWaveRampsLinearly() {
        val gen = SawWaveGenerator()
        assertEquals(-1.0, gen.generate(0.0), 1e-10)
        assertEquals(0.0, gen.generate(PI), 1e-10)
        assertEquals(1.0, gen.generate(2 * PI), 1e-10)
    }

    @Test fun whiteNoiseStaysInRange() {
        val gen = WhiteNoiseGenerator()
        repeat(100) {
            val sample = gen.generate(0.0)
            assertTrue(sample >= -1.0 && sample < 1.0, "noise sample $sample out of [-1, 1)")
        }
    }

    @Test fun whiteNoiseIsNotConstant() {
        val gen = WhiteNoiseGenerator()
        val samples = List(20) { gen.generate(0.0) }
        assertTrue(samples.distinct().size > 1, "white noise should vary between calls")
    }

    @Test fun whiteNoiseIgnoresPhase() {
        val gen = WhiteNoiseGenerator()
        // Phase is unused; calls at any phase should produce in-range samples.
        assertTrue(gen.generate(0.0) in -1.0..1.0)
        assertTrue(gen.generate(PI) in -1.0..1.0)
    }
}
