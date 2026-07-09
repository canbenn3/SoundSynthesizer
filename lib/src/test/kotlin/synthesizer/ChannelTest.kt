package synthesizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChannelTest {
    @Test fun pianoNotesA4Is440Hz() {
        assertEquals(440.0, PianoNotes["A4"]!!, 0.01)
    }

    @Test fun pianoNotesRestIsZero() {
        assertEquals(0.0, PianoNotes["-"]!!)
    }

    @Test fun pianoNotesFlatAndSharpShareFrequency() {
        assertEquals(PianoNotes["C#4"], PianoNotes["Db4"])
        assertEquals(PianoNotes["F#3"], PianoNotes["Gb3"])
    }

    @Test fun singleNoteProducesCorrectSampleCount() {
        val channel = TestHelpers.channel(notesText = "C4 1", beatsPerMeasure = 1.0)
        assertEquals(TestHelpers.samplesForBeats(1.0), channel.getSampleStream().size)
    }

    @Test fun multipleNotesConcatenateSampleCounts() {
        val channel =
                TestHelpers.channel(
                        notesText = "C4 1 D4 2 E4 0.5",
                        beatsPerMeasure = 3.5
                )
        val expected = TestHelpers.samplesForBeats(1.0) +
                TestHelpers.samplesForBeats(2.0) +
                TestHelpers.samplesForBeats(0.5)
        assertEquals(expected, channel.getSampleStream().size)
    }

    @Test fun restNoteIsSilent() {
        val channel = TestHelpers.channel(notesText = "- 1", beatsPerMeasure = 1.0)
        assertTrue(channel.getSampleStream().all { it == 0.0 })
    }

    @Test fun sineNoteStartsAtZeroPhase() {
        val channel = TestHelpers.channel(notesText = "C4 1", beatsPerMeasure = 1.0)
        val stream = channel.getSampleStream()
        val freq = PianoNotes["C4"]!!
        assertEquals(0.0, stream[0], 1e-10)
        val expected = sin(2 * PI * freq / TestHelpers.SAMPLE_RATE)
        assertEquals(expected, stream[1], 1e-10)
    }

    @Test fun phaseResetsAtEachNote() {
        val channel = TestHelpers.channel(notesText = "C4 0.25 C4 0.25", beatsPerMeasure = 0.5)
        val stream = channel.getSampleStream()
        val boundary = TestHelpers.samplesForBeats(0.25)
        assertEquals(0.0, stream[boundary], 1e-10, "phase should reset at note boundary")
    }

    @Test fun squareWaveProducesFullScaleSamples() {
        val channel =
                TestHelpers.channel(
                        waveGenerator = SquareWaveGenerator(),
                        notesText = "C4 1",
                        beatsPerMeasure = 1.0
                )
        val stream = channel.getSampleStream()
        assertTrue(stream.any { it == 1.0 })
        assertTrue(stream.any { it == -1.0 })
    }

    @Test fun tempoAffectsSampleLength() {
        val slow = TestHelpers.channel(notesText = "C4 1", beatsPerMeasure = 1.0, tempo = 60.0)
        val fast = TestHelpers.channel(notesText = "C4 1", beatsPerMeasure = 1.0, tempo = 240.0)
        assertTrue(slow.getSampleStream().size > fast.getSampleStream().size)
        assertEquals(4, slow.getSampleStream().size / fast.getSampleStream().size)
    }

    @Test fun copyConstructorPreservesStream() {
        val original = TestHelpers.channel(notesText = "G4 1", beatsPerMeasure = 1.0)
        val copy = BasicChannel(original)
        assertEquals(original.getSampleStream().toList(), copy.getSampleStream().toList())
    }

    @Test fun mixedRestAndTonePreservesTiming() {
        val channel = TestHelpers.channel(notesText = "- 1 C4 1", beatsPerMeasure = 2.0)
        val stream = channel.getSampleStream()
        val restSamples = TestHelpers.samplesForBeats(1.0)
        assertTrue(stream.take(restSamples).all { it == 0.0 })
        assertTrue(stream.drop(restSamples).any { abs(it) > 0.0 })
    }

    @Test fun parseNotesFromTextBuildsValidChannel() {
        val channel = TestHelpers.channel(notesText = "C4 4")
        assertEquals(TestHelpers.samplesForBeats(4.0), channel.getSampleStream().size)
    }

    @Test fun parseNotesRejectsIncompletePairs() {
        assertFailsWith<ImproperNoteException> {
            Channel.parseNotes("C4", TestHelpers.BEATS_PER_MEASURE)
        }
    }
}
