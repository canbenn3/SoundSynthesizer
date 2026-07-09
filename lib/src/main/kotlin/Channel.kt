package synthesizer

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

object PianoNotes {
    val frequencies: Map<String, Double> = buildMap {
        val sharpNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val flatNames = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

        for (key in 1..88) {
            val freq = 440.0 * 2.0.pow((key - 49) / 12.0)
            val semitonesFromC0 = key + 8
            val pitchClass = semitonesFromC0 % 12
            val octave = semitonesFromC0 / 12
            put("${sharpNames[pitchClass]}$octave", freq)
            if (flatNames[pitchClass] != sharpNames[pitchClass]) {
                put("${flatNames[pitchClass]}$octave", freq)
            }
        }
        put("-", 0.0)
    }

    operator fun get(note: String): Double? = frequencies[note]
}

// A single note: its frequency in Hz (0.0 for a rest) and its length in beats.
data class Note(val frequency: Double, val beats: Double)

abstract class Channel(
        protected val waveGenerator: WaveGenerator,
        protected val sampleRate: Double,
        protected val beatsPerMeasure: Double,
        protected val tempo: Double,
        protected val notes: List<Note>
) {
    // Copy constructor used by ChannelEffect decorators so they share the
    // wrapped channel's timing settings (needed to line effects up per note).
    protected constructor(channel: Channel) : this(
            channel.waveGenerator,
            channel.sampleRate,
            channel.beatsPerMeasure,
            channel.tempo,
            channel.notes
    )

    // Number of audio samples in one beat at the current tempo.
    protected val samplesPerBeat: Double
        get() = sampleRate * 60.0 / tempo

    // How many samples a given note occupies. Centralized so the base channel
    // and any effect decorator agree on note boundaries.
    protected fun noteLengthSamples(note: Note): Int =
            (note.beats * samplesPerBeat).roundToInt()

    abstract fun getSampleStream(): DoubleArray

    companion object {
        // Parse pipe-delimited measure text into a flat list of notes.
        // Each measure is a space-separated sequence of "<note> <duration>" pairs.
        fun parseNotes(measuresText: String, beatsPerMeasure: Double): List<Note> {
            if (measuresText.isBlank()) return emptyList()

            val notes = mutableListOf<Note>()
            val measures = measuresText.split("|")
            for ((index, measure) in measures.withIndex()) {
                val tokens = measure.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (tokens.isEmpty()) continue

                if (tokens.size % 2 != 0) {
                    throw ImproperNoteException(
                            "Measure ${index + 1} has incomplete note/duration pair(s)"
                    )
                }

                var measureBeats = 0.0
                var i = 0
                while (i + 1 < tokens.size) {
                    val name = tokens[i]
                    val beats =
                            try {
                                tokens[i + 1].toDouble()
                            } catch (_: NumberFormatException) {
                                throw ImproperNoteException(
                                        "Measure ${index + 1}: invalid duration '${tokens[i + 1]}'"
                                )
                            }
                    val frequency =
                            PianoNotes[name]
                                    ?: throw ImproperNoteException("Unknown note '$name'")
                    notes.add(Note(frequency, beats))
                    measureBeats += beats
                    i += 2
                }

                if (abs(measureBeats - beatsPerMeasure) > 1e-9) {
                    throw ImproperNoteException(
                            "Measure ${index + 1} has $measureBeats beats, expected $beatsPerMeasure"
                    )
                }
            }
            return notes
        }
    }
}

// Baseline channel: renders notes from a wave generator without effects.
class BasicChannel : Channel {
    constructor(
            waveGenerator: WaveGenerator,
            sampleRate: Double,
            beatsPerMeasure: Double,
            tempo: Double,
            notesText: String
    ) : super(
            waveGenerator,
            sampleRate,
            beatsPerMeasure,
            tempo,
            parseNotes(notesText, beatsPerMeasure)
    )

    constructor(
            waveGenerator: WaveGenerator,
            sampleRate: Double,
            beatsPerMeasure: Double,
            tempo: Double,
            notes: List<Note>
    ) : super(waveGenerator, sampleRate, beatsPerMeasure, tempo, notes)

    constructor(channel: Channel) : super(channel)

    override fun getSampleStream(): DoubleArray {
        val totalSamples = notes.sumOf { noteLengthSamples(it) }
        val stream = DoubleArray(totalSamples)

        var index = 0
        val twoPi = 2 * Math.PI
        for (note in notes) {
            val length = noteLengthSamples(note)
            val phaseIncrement = twoPi * note.frequency / sampleRate
            var phase = 0.0
            for (i in 0 until length) {
                stream[index++] = if (note.frequency == 0.0) 0.0 else waveGenerator.generate(phase)
                phase = (phase + phaseIncrement) % twoPi
            }
        }
        return stream
    }
}
