package synthesizer

import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

data class FileHeader(val sampleRate: Double, val beatsPerMeasure: Double, val tempo: Double)

data class ChannelHeader(val waveForm: String, val effects: List<String>)

fun sine(phase: Double): Double {
    return sin(phase)
}

fun normalize(samples: DoubleArray) {
    val max = samples.maxOfOrNull { abs(it) } ?: 0.0
    if (max == 0.0) return
    for (i in samples.indices) {
        samples[i] /= max
    }
}

class Synthesizer {
    fun someLibraryMethod(): Boolean {
        return true
    }

    fun playFile(filename: String) {
        val header = readFileHeader(readFile(filename).lines().first { it.isNotBlank() })
        // 6. Play that sucker!
        playStream(renderFile(filename), header.sampleRate)
    }

    // Steps 1-5 of the pipeline: read the song, build every channel, and mix
    // them into a single normalized sample stream. Kept separate from playback
    // so the synthesis can be exercised without audio hardware.
    fun renderFile(filename: String): DoubleArray {
        // 1. Read the file
        val contents = readFile(filename)
        val lines = contents.lines().filter { it.isNotBlank() }
        require(lines.isNotEmpty()) { "Song file '$filename' is empty." }

        // 2. Extract the settings from the file header
        val header = readFileHeader(lines.first())

        // 3. Create channels from each new line of the file (excluding header)
        val streams = lines.drop(1).map { line ->
            // 3a. Read channel headers and apply all specified decorators and settings
            buildChannel(line, header).getSampleStream()
        }

        // 4 & 5. Mix and normalize streams
        return mixAndNormalizeStreams(streams)
    }

    // Turn one channel line ("waveform effects...|measure|measure|") into a fully
    // decorated Channel.
    private fun buildChannel(line: String, header: FileHeader): Channel {
        val sections = line.split("|")
        val channelHeader = readChannelHeader(sections.first())
        val notes = parseNotes(sections.drop(1))

        var channel: Channel = Channel(
                waveGeneratorFor(channelHeader.waveForm),
                header.sampleRate,
                header.beatsPerMeasure,
                header.tempo,
                notes
        )

        for (effect in channelHeader.effects) {
            channel = applyEffect(channel, effect)
        }
        return channel
    }

    // Parse the measures of a channel line into a flat list of notes.
    // Each measure is a space separated sequence of "<note> <duration>" pairs.
    private fun parseNotes(measures: List<String>): List<Note> {
        val notes = mutableListOf<Note>()
        for (measure in measures) {
            val tokens = measure.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            var i = 0
            while (i + 1 < tokens.size) {
                val name = tokens[i]
                val beats = tokens[i + 1].toDouble()
                val frequency = PianoNotes[name]
                        ?: throw IllegalArgumentException("Unknown note '$name'")
                notes.add(Note(frequency, beats))
                i += 2
            }
        }
        return notes
    }

    private fun waveGeneratorFor(waveForm: String): WaveGenerator = when (waveForm) {
        "sin" -> SineWaveGenerator()
        "square" -> SquareWaveGenerator()
        "saw" -> SawWaveGenerator()
        "noise" -> WhiteNoiseGenerator()
        else -> throw IllegalArgumentException("Unknown waveform '$waveForm'")
    }

    // Wrap a channel in the decorator named by an effect token like "vol$0.3".
    private fun applyEffect(channel: Channel, effect: String): Channel {
        val parts = effect.split("$")
        val name = parts.first()
        val params = parts.drop(1).map { it.toDouble() }
        return when (name) {
            "vol" -> VolumeEffect(channel, params[0])
            "ads" -> AttackDecaySustainEffect(channel, params[0], params[1], params[2])
            "tanh" -> TanhEffect(channel, params[0])
            "clip" -> ClipEffect(channel, params[0])
            else -> throw IllegalArgumentException("Unknown effect '$name'")
        }
    }

    // Convert a file into a string
    private fun readFile(filename: String): String {
        // Open the file, read the contents, close the file, and return the contents as a string
        return File(filename).readText()
    }

    // Extract the parameters from the file header
    private fun readFileHeader(line: String): FileHeader {
        val parts = line.trim().split(Regex("\\s+"))
        return FileHeader(parts[0].toDouble(), parts[1].toDouble(), parts[2].toDouble())
    }

    // Extract wave types and channel effects from a channel line.
    private fun readChannelHeader(line: String): ChannelHeader {
        val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val waveForm = tokens.first()
        val effects = tokens.drop(1)
        return ChannelHeader(waveForm, effects)
    }

    private fun mixAndNormalizeStreams(streams: List<DoubleArray>): DoubleArray {
        if (streams.isEmpty()) return DoubleArray(0)

        val length = streams.maxOf { it.size }
        val mixed = DoubleArray(length)
        for (stream in streams) {
            for (i in stream.indices) {
                mixed[i] += stream[i]
            }
        }
        normalize(mixed)
        return mixed
    }

    private fun playStream(stream: DoubleArray, sampleRate: Double) {
        if (stream.isEmpty()) return

        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val line: SourceDataLine = AudioSystem.getSourceDataLine(format)
        line.open(format)
        line.start()

        val buffer = ByteArray(stream.size * 2)
        for (i in stream.indices) {
            val clamped = stream[i].coerceIn(-1.0, 1.0)
            val value = (clamped * Short.MAX_VALUE).toInt()
            buffer[i * 2] = (value and 0xFF).toByte() // low byte
            buffer[i * 2 + 1] = (value shr 8 and 0xFF).toByte() // high byte
        }

        line.write(buffer, 0, buffer.size)
        line.drain() // block until every sample has finished playing
        line.stop()
        line.close()
    }

    fun run() {
        val sampleRate = 44100
        val duration = 2.0
        val totalSamples = sampleRate * duration
        val frequency1 = 220.0
        val frequency2 = 261.63
        val phaseIncrement1 = 2 * PI * frequency1 / sampleRate
        val phaseIncrement2 = 2 * PI * frequency2 / sampleRate
        val samples1 = DoubleArray(totalSamples.toInt()) { 0.0 }
        val samples2 = DoubleArray(totalSamples.toInt()) { 0.0 }
        for (n in samples2.indices) {
            samples2[n] = sine(n * phaseIncrement2 % (2 * PI))
        }
        for (n in samples1.indices) {
            samples1[n] = sine(n * phaseIncrement1 % (2 * PI))
        }

        val samples = DoubleArray(max(samples2.size, samples1.size))

        for (i in samples.indices) {
            samples[i] = samples1[i] + samples2[i]
        }

        normalize(samples)

        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val line: SourceDataLine = AudioSystem.getSourceDataLine(format)
        line.open(format)
        line.start()

        val buffer = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val clamped = samples[i].coerceIn(-1.0, 1.0)
            val value = (clamped * Short.MAX_VALUE).toInt()
            buffer[i * 2] = (value and 0xFF).toByte() // low byte
            buffer[i * 2 + 1] = (value shr 8 and 0xFF).toByte() // high byte
        }

        line.write(buffer, 0, buffer.size)
        line.drain() // block until every sample has finished playing
        line.stop()
        line.close()
    }
}
