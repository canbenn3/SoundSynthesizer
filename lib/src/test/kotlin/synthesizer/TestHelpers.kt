package synthesizer

import java.io.File
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.math.roundToInt

object TestHelpers {
    const val SAMPLE_RATE = 44100.0
    const val BEATS_PER_MEASURE = 4.0
    const val TEMPO = 120.0

    fun samplesForBeats(
            beats: Double,
            sampleRate: Double = SAMPLE_RATE,
            tempo: Double = TEMPO
    ): Int = (beats * sampleRate * 60.0 / tempo).roundToInt()

    fun songsDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "songs_and_recordings/songs")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        throw IllegalStateException("Could not locate songs_and_recordings/songs")
    }

    fun song(name: String) = File(songsDir(), name).path

    fun writeTempSong(content: String): String {
        val file = createTempFile(prefix = "song", suffix = ".txt")
        file.writeText(content)
        return file.toAbsolutePath().toString()
    }

    fun channel(
            waveGenerator: WaveGenerator = SineWaveGenerator(),
            notesText: String,
            sampleRate: Double = SAMPLE_RATE,
            beatsPerMeasure: Double = BEATS_PER_MEASURE,
            tempo: Double = TEMPO
    ) = BasicChannel(waveGenerator, sampleRate, beatsPerMeasure, tempo, notesText)

    fun measure(vararg pairs: Pair<String, Double>): String =
            pairs.joinToString(" ") { (name, beats) -> "$name $beats" }

    fun measureBeats(vararg pairs: Pair<String, Double>): Double = pairs.sumOf { it.second }
}
