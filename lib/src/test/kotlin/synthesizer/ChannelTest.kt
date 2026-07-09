package synthesizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val channel = TestHelpers.channel(notes = listOf(TestHelpers.note("C4", 1.0)))
        assertEquals(TestHelpers.samplesForBeats(1.0), channel.getSampleStream().size)
    }

    @Test fun multipleNotesConcatenateSampleCounts() {
        val notes = listOf(TestHelpers.note("C4", 1.0), TestHelpers.note("D4", 2.0), TestHelpers.note("E4", 0.5))
        val channel = TestHelpers.channel(notes = notes)
        val expected = TestHelpers.samplesForBeats(1.0) +
                TestHelpers.samplesForBeats(2.0) +
                TestHelpers.samplesForBeats(0.5)
        assertEquals(expected, channel.getSampleStream().size)
    }

    @Test fun restNoteIsSilent() {
        val channel = TestHelpers.channel(notes = listOf(Note(0.0, 1.0)))
        assertTrue(channel.getSampleStream().all { it == 0.0 })
    }

    @Test fun sineNoteStartsAtZeroPhase() {
        val freq = PianoNotes["C4"]!!
        val channel = TestHelpers.channel(notes = listOf(Note(freq, 1.0)))
        val stream = channel.getSampleStream()
        assertEquals(0.0, stream[0], 1e-10)
        val expected = sin(2 * PI * freq / TestHelpers.SAMPLE_RATE)
        assertEquals(expected, stream[1], 1e-10)
    }

    @Test fun phaseResetsAtEachNote() {
        val freq = PianoNotes["C4"]!!
        val channel = TestHelpers.channel(notes = listOf(Note(freq, 0.25), Note(freq, 0.25)))
        val stream = channel.getSampleStream()
        val boundary = TestHelpers.samplesForBeats(0.25)
        assertEquals(0.0, stream[boundary], 1e-10, "phase should reset at note boundary")
    }

    @Test fun squareWaveProducesFullScaleSamples() {
        val channel = TestHelpers.channel(
                waveGenerator = SquareWaveGenerator(),
                notes = listOf(TestHelpers.note("C4", 1.0))
        )
        val stream = channel.getSampleStream()
        assertTrue(stream.any { it == 1.0 })
        assertTrue(stream.any { it == -1.0 })
    }

    @Test fun tempoAffectsSampleLength() {
        val notes = listOf(TestHelpers.note("C4", 1.0))
        val slow = TestHelpers.channel(notes = notes, tempo = 60.0)
        val fast = TestHelpers.channel(notes = notes, tempo = 240.0)
        assertTrue(slow.getSampleStream().size > fast.getSampleStream().size)
        assertEquals(4, slow.getSampleStream().size / fast.getSampleStream().size)
    }

    @Test fun copyConstructorPreservesStream() {
        val original = TestHelpers.channel(notes = listOf(TestHelpers.note("G4", 1.0)))
        val copy = Channel(original)
        assertEquals(original.getSampleStream().toList(), copy.getSampleStream().toList())
    }

    @Test fun mixedRestAndTonePreservesTiming() {
        val notes = listOf(Note(0.0, 1.0), TestHelpers.note("C4", 1.0))
        val channel = TestHelpers.channel(notes = notes)
        val stream = channel.getSampleStream()
        val restSamples = TestHelpers.samplesForBeats(1.0)
        assertTrue(stream.take(restSamples).all { it == 0.0 })
        assertTrue(stream.drop(restSamples).any { abs(it) > 0.0 })
    }
}
