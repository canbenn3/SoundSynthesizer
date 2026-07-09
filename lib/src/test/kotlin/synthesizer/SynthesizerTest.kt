package synthesizer

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SynthesizerTest {
    private val synth = Synthesizer()

    @Test fun rejectsMissingSongFile() {
        assertFailsWith<SongFileNotFoundException> {
            synth.renderFile("/nonexistent/path/song.txt")
        }
    }

    @Test fun rejectsEmptySongFile() {
        val path = TestHelpers.writeTempSong("")
        assertFailsWith<IllegalFileFormatException> { synth.renderFile(path) }
    }

    @Test fun rejectsImproperFileHeader() {
        val path = TestHelpers.writeTempSong("44100 4\nsin|C4 4")
        assertFailsWith<ImproperFileHeaderException> { synth.renderFile(path) }
    }

    @Test fun rejectsNonNumericFileHeader() {
        val path = TestHelpers.writeTempSong("fast 4 120\nsin|C4 4")
        assertFailsWith<ImproperFileHeaderException> { synth.renderFile(path) }
    }

    @Test fun rejectsUnknownNote() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin|X9 4")
        assertFailsWith<ImproperNoteException> { synth.renderFile(path) }
    }

    @Test fun rejectsUnknownWaveform() {
        val path = TestHelpers.writeTempSong("44100 4 120\ntriangle|C4 4")
        assertFailsWith<ImproperChannelHeaderException> { synth.renderFile(path) }
    }

    @Test fun rejectsUnknownEffect() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin reverb$0.5|C4 4")
        assertFailsWith<ImproperChannelHeaderException> { synth.renderFile(path) }
    }

    @Test fun rejectsMeasureWithTooFewBeats() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin|C4 3")
        val error = assertFailsWith<ImproperNoteException> { synth.renderFile(path) }
        assertTrue(error.message!!.contains("3") && error.message!!.contains("expected 4"))
    }

    @Test fun rejectsMeasureWithTooManyBeats() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin|C4 1 C4 1 C4 1 C4 1 C4 1")
        val error = assertFailsWith<ImproperNoteException> { synth.renderFile(path) }
        assertTrue(error.message!!.contains("5") && error.message!!.contains("expected 4"))
    }

    @Test fun rejectsMeasureWithMismatchedBeatsAcrossMeasures() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin|C4 4|D4 3")
        val error = assertFailsWith<ImproperNoteException> { synth.renderFile(path) }
        assertTrue(error.message!!.contains("Measure 2"))
    }

    @Test fun rejectsChannelWithoutWaveform() {
        val path = TestHelpers.writeTempSong("44100 4 120\nvol$0.5|C4 4")
        val error = assertFailsWith<ImproperChannelHeaderException> { synth.renderFile(path) }
        assertTrue(error.message!!.contains("waveform"))
    }

    @Test fun rejectsChannelStartingWithEffectName() {
        val path = TestHelpers.writeTempSong("44100 4 120\nads$0.1$0.2$0.3|C4 4")
        val error = assertFailsWith<ImproperChannelHeaderException> { synth.renderFile(path) }
        assertTrue(error.message!!.contains("waveform"))
    }

    @Test fun rejectsEmptyChannelHeader() {
        val path = TestHelpers.writeTempSong("44100 4 120\n|C4 4")
        val error = assertFailsWith<ImproperChannelHeaderException> { synth.renderFile(path) }
        assertTrue(error.message!!.contains("waveform"))
    }

    @Test fun rejectsEffectWithNoArguments() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin vol|C4 4")
        val error = assertFailsWith<ImproperChannelHeaderException> { synth.renderFile(path) }
        assertTrue(error.message!!.contains("not enough arguments"))
    }

    @Test fun rejectsEffectWithMissingParameter() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin vol$|C4 4")
        val error = assertFailsWith<ImproperChannelHeaderException> { synth.renderFile(path) }
        assertTrue(error.message!!.contains("not enough arguments"))
    }

    @Test fun rejectsAdsEffectWithTooFewArguments() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin ads$0.1$0.2|C4 4")
        val error = assertFailsWith<ImproperChannelHeaderException> { synth.renderFile(path) }
        assertTrue(error.message!!.contains("ads requires 3 arguments"))
    }

    @Test fun rejectsTanhEffectWithTooFewArguments() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin tanh|C4 4")
        val error = assertFailsWith<ImproperChannelHeaderException> { synth.renderFile(path) }
        assertTrue(error.message!!.contains("not enough arguments"))
    }

    @Test fun rendersAllSupportedWaveforms() {
        for (song in
                listOf(
                        "wave_sine.txt",
                        "wave_square.txt",
                        "wave_saw.txt",
                        "wave_noise.txt"
                )) {
            val samples = synth.renderFile(TestHelpers.song(song))
            assertTrue(samples.isNotEmpty(), "$song should produce output")
            assertEquals(TestHelpers.samplesForBeats(16.0), samples.size, "$song is 16 beats long")
        }
    }

    @Test fun rendersWhitenoiseWaveform() {
        val path = TestHelpers.writeTempSong("44100 4 120\nwhitenoise|C4 1 D4 1 E4 1 F4 1")
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
        val path = TestHelpers.writeTempSong("44100 4 120\nsin|C4 .5 D4 .5 E4 .5 F4 .5 G4 1 G4 1")
        val samples = synth.renderFile(path)
        assertEquals(TestHelpers.samplesForBeats(4.0), samples.size)
    }

    @Test fun multipleMeasuresConcatenate() {
        val path = TestHelpers.writeTempSong("44100 4 120\nsin|C4 4|D4 4|E4 4|F4 4")
        val samples = synth.renderFile(path)
        assertEquals(TestHelpers.samplesForBeats(16.0), samples.size)
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
