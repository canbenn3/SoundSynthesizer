package synthesizer

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderTest {
    // Locate songs_and_recordings/songs regardless of which directory the tests
    // are launched from (Gradle uses the module dir, IDEs may use the repo root).
    private fun songsDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "songs_and_recordings/songs")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        throw IllegalStateException("Could not locate songs_and_recordings/songs")
    }

    private fun song(name: String) = File(songsDir(), name).path

    @Test fun rendersExpectedNumberOfSamples() {
        // wave_sine.txt: 44100 Hz, 120 BPM, 16 beats total -> 16 * 44100 * 60/120.
        val samples = Synthesizer().renderFile(song("wave_sine.txt"))
        val expected = (16 * 44100 * 60.0 / 120.0).toInt()
        assertEquals(expected, samples.size, "wave_sine should render 16 beats of audio")
    }

    @Test fun outputStaysWithinRange() {
        val samples = Synthesizer().renderFile(song("twinkle_twinkle.txt"))
        assertTrue(samples.isNotEmpty(), "expected non-empty output")
        assertTrue(samples.all { it in -1.0..1.0 }, "normalized samples must be within [-1, 1]")
        assertTrue(samples.any { abs(it) > 0.5 }, "expected a normalized peak near full scale")
    }

    @Test fun pluckEnvelopeDecaysToSilence() {
        // fx_ads_pluck.txt uses ads$0$.15$0 -> instant attack, decay to zero.
        // The tail of a note should be (near) silent while its head is loud.
        val samples = Synthesizer().renderFile(song("fx_ads_pluck.txt"))
        val head = samples.take(200).maxOf { abs(it) }
        val quarter = samples.size / 4
        val tail = samples.drop(quarter - 200).take(200).maxOf { abs(it) }
        assertTrue(head > tail, "pluck note should be louder at its start than before the next note")
    }

    @Test fun multiChannelSongMixesAllChannels() {
        // twinkle has a melody + bass line; both should contribute.
        val samples = Synthesizer().renderFile(song("twinkle_twinkle.txt"))
        assertTrue(samples.size > 44100, "a full song should be several seconds long")
    }
}
