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
            notes: List<Note>,
            sampleRate: Double = SAMPLE_RATE,
            beatsPerMeasure: Double = BEATS_PER_MEASURE,
            tempo: Double = TEMPO
    ) = Channel(waveGenerator, sampleRate, beatsPerMeasure, tempo, notes)

    fun note(name: String, beats: Double) = Note(PianoNotes[name]!!, beats)
}
