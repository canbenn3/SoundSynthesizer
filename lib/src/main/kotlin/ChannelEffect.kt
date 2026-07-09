package synthesizer

import kotlin.math.tanh

// Decorator base: an effect is itself a Channel that wraps another Channel and
// transforms the samples produced by it.
abstract class ChannelEffect(protected val channel: Channel) : Channel(channel) {
    abstract override fun getSampleStream(): DoubleArray
}

// vol$level — scales the channel's amplitude.
class VolumeEffect(channel: Channel, private val level: Double) : ChannelEffect(channel) {
    override fun getSampleStream(): DoubleArray {
        val stream = channel.getSampleStream()
        for (i in stream.indices) {
            stream[i] *= level
        }
        return stream
    }
}

// ads$attackEnd$decayEnd$sustain — Attack-Decay-Sustain envelope, restarted per note.
// attackEnd: time (seconds) at which the attack ramp ends.
// decayEnd:  time (seconds) at which the decay ends.
// sustain:   level held afterward until the note ends.
class AttackDecaySustainEffect(
        channel: Channel,
        private val attackEnd: Double,
        private val decayEnd: Double,
        private val sustain: Double
) : ChannelEffect(channel) {
    override fun getSampleStream(): DoubleArray {
        val stream = channel.getSampleStream()
        var index = 0
        for (note in notes) {
            val length = noteLengthSamples(note)
            for (i in 0 until length) {
                if (index >= stream.size) break
                stream[index] *= envelope(i / sampleRate)
                index++
            }
        }
        return stream
    }

    private fun envelope(secondsIntoNote: Double): Double = when {
        attackEnd > 0.0 && secondsIntoNote < attackEnd -> secondsIntoNote / attackEnd
        secondsIntoNote < decayEnd -> {
            if (decayEnd <= attackEnd) sustain
            else 1.0 - (1.0 - sustain) * (secondsIntoNote - attackEnd) / (decayEnd - attackEnd)
        }
        else -> sustain
    }
}

// tanh$drive — tanh distortion; drive is the amount of drive applied.
class TanhEffect(channel: Channel, private val drive: Double) : ChannelEffect(channel) {
    override fun getSampleStream(): DoubleArray {
        val stream = channel.getSampleStream()
        for (i in stream.indices) {
            stream[i] = tanh(drive * stream[i])
        }
        return stream
    }
}

// clip$threshold — clip distortion; signal is clamped to +/- threshold.
class ClipEffect(channel: Channel, private val threshold: Double) : ChannelEffect(channel) {
    override fun getSampleStream(): DoubleArray {
        val stream = channel.getSampleStream()
        for (i in stream.indices) {
            stream[i] = stream[i].coerceIn(-threshold, threshold)
        }
        return stream
    }
}
