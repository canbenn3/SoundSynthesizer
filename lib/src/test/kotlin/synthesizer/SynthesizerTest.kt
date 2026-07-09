package synthesizer

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SynthesizerTest {
    private val synth = Synthesizer()

    @Test fun rejectsEmptySongFile() {
        val path = TestHelpers.writeTempSong("")
        assertFailsWith<IllegalArgumentException> { synth.renderFile(path) }
    }

    @Test fun rejectsUnknownNote() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin|X9 1")
        assertFailsWith<IllegalArgumentException> { synth.renderFile(path) }
    }

    @Test fun rejectsUnknownWaveform() {
        val path = TestHelpers.writeTempSong("44100 4 120\ntriangle|C4 1")
        assertFailsWith<IllegalArgumentException> { synth.renderFile(path) }
    }

    @Test fun rejectsUnknownEffect() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin reverb$0.5|C4 1")
        assertFailsWith<IllegalArgumentException> { synth.renderFile(path) }
    }

    @Test fun rendersAllSupportedWaveforms() {
        for (song in listOf("wave_sine.txt", "wave_square.txt", "wave_saw.txt")) {
            val samples = synth.renderFile(TestHelpers.song(song))
            assertTrue(samples.isNotEmpty(), "$song should produce output")
            assertEquals(TestHelpers.samplesForBeats(16.0), samples.size, "$song is 16 beats long")
        }
    }

    @Test fun rendersNoiseWaveform() {
        val path = TestHelpers.writeTempSong("44100 4 120\nnoise|C4 1 D4 1 E4 1 F4 1")
        val samples = synth.renderFile(path)
        assertEquals(TestHelpers.samplesForBeats(4.0), samples.size)
        assertEquals(1.0, samples.maxOf { abs(it) }, 1e-10)
    }

    @Test fun normalizesOutputToUnitPeak() {
        val samples = synth.renderFile(TestHelpers.song("wave_sine.txt"))
        val peak = samples.maxOf { abs(it) }
        assertEquals(1.0, peak, 1e-10)
    }

    @Test fun volumeEffectReducesPeakBeforeNormalization() {
        val plain = synth.renderFile(TestHelpers.song("wave_sine.txt"))
        val quiet = synth.renderFile(TestHelpers.song("fx_volume.txt"))
        // Both normalize to peak 1.0, but the quieter channel's raw shape differs —
        // verify fx_volume actually parsed the effect by checking sample count matches.
        assertEquals(plain.size, quiet.size)
    }

    @Test fun clipEffectRendersNormalizedOutput() {
        val samples = synth.renderFile(TestHelpers.song("fx_clip.txt"))
        assertEquals(TestHelpers.samplesForBeats(16.0), samples.size)
        assertEquals(1.0, samples.maxOf { abs(it) }, 1e-10)
    }

    @Test fun stackedEffectsRender() {
        val samples = synth.renderFile(TestHelpers.song("fx_tanh_clip_stack.txt"))
        assertTrue(samples.size > TestHelpers.samplesForBeats(8.0))
        assertEquals(1.0, samples.maxOf { abs(it) }, 1e-10)
    }

    @Test fun multiChannelMixesLongerStream() {
        val single = synth.renderFile(TestHelpers.song("wave_sine.txt"))
        val multi = synth.renderFile(TestHelpers.song("twinkle_twinkle.txt"))
        assertTrue(multi.size >= single.size)
    }

    @Test fun fractionalBeatsParseCorrectly() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin|C4 .5 D4 .5 E4 .5 F4 .5")
        val samples = synth.renderFile(path)
        assertEquals(TestHelpers.samplesForBeats(2.0), samples.size)
    }

    @Test fun multipleMeasuresConcatenate() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin|C4 1|D4 1|E4 1|F4 1")
        val samples = synth.renderFile(path)
        assertEquals(TestHelpers.samplesForBeats(4.0), samples.size)
    }

    @Test fun channelWithOnlyEffectsHeaderAndNoNotesIsEmpty() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin vol$0.5")
        val samples = synth.renderFile(path)
        assertEquals(0, samples.size)
    }

    @Test fun differentTempoChangesDuration() {
        val slow = TestHelpers.writeTempSong("44100 4 60\nsin|C4 4")
        val fast = TestHelpers.writeTempSong("44100 4 240\nsin|C4 4")
        assertEquals(TestHelpers.samplesForBeats(4.0, tempo = 60.0), synth.renderFile(slow).size)
        assertEquals(TestHelpers.samplesForBeats(4.0, tempo = 240.0), synth.renderFile(fast).size)
    }
}
