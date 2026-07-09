package synthesizer

import java.io.File
import java.io.IOException
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.abs

data class FileHeader(val sampleRate: Double, val beatsPerMeasure: Double, val tempo: Double)

data class ChannelHeader(val waveForm: String, val effects: List<String>)

class Synthesizer {
    private val waveForms = setOf("sin", "square", "saw", "whitenoise")
    private val effectNames = setOf("vol", "ads", "tanh", "clip")

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
        if (lines.isEmpty()) {
            throw IllegalFileFormatException("Song file '$filename' is empty.")
        }

        // 2. Extract the settings from the file header
        val header = readFileHeader(lines.first())

        // 3. Create channels from each new line of the file (excluding header)
        val streams =
                lines.drop(1).map { line ->
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
        val notesText = sections.drop(1).joinToString("|")

        var channel: Channel =
                BasicChannel(
                        waveGeneratorFor(channelHeader.waveForm),
                        header.sampleRate,
                        header.beatsPerMeasure,
                        header.tempo,
                        notesText
                )

        for (effect in channelHeader.effects) {
            channel = applyEffect(channel, effect)
        }
        return channel
    }

    private fun waveGeneratorFor(waveForm: String): WaveGenerator =
            when (waveForm) {
                "sin" -> SineWaveGenerator()
                "square" -> SquareWaveGenerator()
                "saw" -> SawWaveGenerator()
                "whitenoise" -> WhiteNoiseGenerator()
                else ->
                        throw ImproperChannelHeaderException("Unknown waveform '$waveForm'")
            }

    // Wrap a channel in the decorator named by an effect token like "vol$0.3".
    private fun applyEffect(channel: Channel, effect: String): Channel {
        val parts = effect.split("$")
        val name = parts.first()
        if (name.isEmpty()) {
            throw ImproperChannelHeaderException("Improperly formatted effect '$effect'")
        }

        val paramStrings = parts.drop(1)
        if (paramStrings.isEmpty() || paramStrings.any { it.isEmpty() }) {
            throw ImproperChannelHeaderException(
                    "Improperly formatted effect '$effect': not enough arguments"
            )
        }

        val params =
                try {
                    paramStrings.map { it.toDouble() }
                } catch (_: NumberFormatException) {
                    throw ImproperChannelHeaderException(
                            "Improperly formatted effect '$effect': arguments must be numeric"
                    )
                }

        return when (name) {
            "vol" -> {
                if (params.size < 1) {
                    throw ImproperChannelHeaderException(
                            "Improperly formatted effect '$effect': vol requires 1 argument"
                    )
                }
                VolumeEffect(channel, params[0])
            }
            "ads" -> {
                if (params.size < 3) {
                    throw ImproperChannelHeaderException(
                            "Improperly formatted effect '$effect': ads requires 3 arguments"
                    )
                }
                AttackDecaySustainEffect(channel, params[0], params[1], params[2])
            }
            "tanh" -> {
                if (params.size < 1) {
                    throw ImproperChannelHeaderException(
                            "Improperly formatted effect '$effect': tanh requires 1 argument"
                    )
                }
                TanhEffect(channel, params[0])
            }
            "clip" -> {
                if (params.size < 1) {
                    throw ImproperChannelHeaderException(
                            "Improperly formatted effect '$effect': clip requires 1 argument"
                    )
                }
                ClipEffect(channel, params[0])
            }
            else -> throw ImproperChannelHeaderException("Unknown effect '$name'")
        }
    }

    private fun readFile(filename: String): String {
        val file = File(filename)
        if (!file.exists()) {
            throw SongFileNotFoundException("Song file not found: '$filename'")
        }
        if (!file.isFile) {
            throw SongFileNotFoundException("Path is not a file: '$filename'")
        }
        return try {
            file.readText()
        } catch (e: IOException) {
            throw IllegalFileFormatException(
                    "Could not read song file '$filename': ${e.message}"
            )
        }
    }

    private fun readFileHeader(line: String): FileHeader {
        val parts = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size != 3) {
            throw ImproperFileHeaderException(
                    "File header must have exactly 3 values (sampleRate beatsPerMeasure tempo), " +
                            "found ${parts.size}"
            )
        }

        val sampleRate = parseHeaderDouble(parts[0], "sample rate")
        val beatsPerMeasure = parseHeaderDouble(parts[1], "beats per measure")
        val tempo = parseHeaderDouble(parts[2], "tempo")
        return FileHeader(sampleRate, beatsPerMeasure, tempo)
    }

    private fun parseHeaderDouble(value: String, fieldName: String): Double =
            try {
                value.toDouble()
            } catch (_: NumberFormatException) {
                throw ImproperFileHeaderException(
                        "File header $fieldName '$value' is not a valid number"
                )
            }

    private fun readChannelHeader(line: String): ChannelHeader {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            throw ImproperChannelHeaderException("Channel must specify a waveform")
        }

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            throw ImproperChannelHeaderException("Channel must specify a waveform")
        }

        val waveFormToken = tokens.first()
        if (waveFormToken !in waveForms) {
            val effectName = waveFormToken.substringBefore("$")
            if (effectName in effectNames) {
                throw ImproperChannelHeaderException("Channel must specify a waveform")
            }
            throw ImproperChannelHeaderException("Unknown waveform '$waveFormToken'")
        }

        return ChannelHeader(waveFormToken, tokens.drop(1))
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

    private fun normalize(samples: DoubleArray) {
        val peak = samples.maxOfOrNull { abs(it) } ?: 0.0
        if (peak == 0.0) return
        for (i in samples.indices) {
            samples[i] /= peak
        }
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
}
