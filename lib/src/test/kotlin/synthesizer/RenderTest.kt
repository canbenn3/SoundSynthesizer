package synthesizer

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderTest {
    @Test fun rendersExpectedNumberOfSamples() {
        // wave_sine.txt: 44100 Hz, 120 BPM, 16 beats total -> 16 * 44100 * 60/120.
        val samples = Synthesizer().renderFile(TestHelpers.song("wave_sine.txt"))
        assertEquals(TestHelpers.samplesForBeats(16.0), samples.size, "wave_sine should render 16 beats of audio")
    }

    @Test fun outputStaysWithinRange() {
        val samples = Synthesizer().renderFile(TestHelpers.song("twinkle_twinkle.txt"))
        assertTrue(samples.isNotEmpty(), "expected non-empty output")
        assertTrue(samples.all { it in -1.0..1.0 }, "normalized samples must be within [-1, 1]")
        assertTrue(samples.any { abs(it) > 0.5 }, "expected a normalized peak near full scale")
    }

    @Test fun pluckEnvelopeDecaysToSilence() {
        // fx_ads_pluck.txt uses ads$0$.15$0 -> instant attack, decay to zero.
        val samples = Synthesizer().renderFile(TestHelpers.song("fx_ads_pluck.txt"))
        val head = samples.take(200).maxOf { abs(it) }
        val quarter = samples.size / 4
        val tail = samples.drop(quarter - 200).take(200).maxOf { abs(it) }
        assertTrue(head > tail, "pluck note should be louder at its start than before the next note")
    }

    @Test fun multiChannelSongMixesAllChannels() {
        val samples = Synthesizer().renderFile(TestHelpers.song("twinkle_twinkle.txt"))
        assertTrue(samples.size > TestHelpers.SAMPLE_RATE.toInt(), "a full song should be several seconds long")
    }

    @Test fun eachWaveformFixtureRenders() {
        val synth = Synthesizer()
        for (name in listOf("wave_sine.txt", "wave_square.txt", "wave_saw.txt", "wave_noise.txt")) {
            val samples = synth.renderFile(TestHelpers.song(name))
            assertEquals(TestHelpers.samplesForBeats(16.0), samples.size, name)
            assertEquals(1.0, samples.maxOf { abs(it) }, 1e-10, "$name should normalize to full scale")
        }
    }

    @Test fun effectFixturesRenderWithExpectedLength() {
        val synth = Synthesizer()
        val cases =
                listOf(
                        Triple("fx_volume.txt", 16.0, 120.0),
                        Triple("fx_tanh.txt", 16.0, 120.0),
                        Triple("fx_clip.txt", 16.0, 120.0),
                        Triple("fx_ads_pluck.txt", 16.0, 120.0),
                        Triple("fx_ads_pad.txt", 16.0, 80.0)
                )
        for ((file, beats, tempo) in cases) {
            val samples = synth.renderFile(TestHelpers.song(file))
            assertEquals(TestHelpers.samplesForBeats(beats, tempo = tempo), samples.size, file)
        }
    }

    @Test fun longerSongsRenderWithoutError() {
        val synth = Synthesizer()
        for (name in
                listOf(
                        "mary_had_a_little_lamb.txt",
                        "ode_to_joy.txt",
                        "amazing_grace.txt",
                        "frere_jacques_round.txt",
                        "jingle_bells.txt"
                )) {
            val samples = synth.renderFile(TestHelpers.song(name))
            assertTrue(samples.isNotEmpty(), name)
            assertTrue(samples.all { it in -1.0..1.0 }, "$name samples out of range")
        }
    }
}
