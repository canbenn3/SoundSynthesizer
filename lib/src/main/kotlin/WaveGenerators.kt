package synthesizer

import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

interface WaveGenerator {
    fun generate(phase: Double): Double
}

class SineWaveGenerator() : WaveGenerator {
    override fun generate(phase: Double): Double {
        return sin(phase)
    }
}

class SquareWaveGenerator() : WaveGenerator {
    override fun generate(phase: Double): Double {
        if (phase < PI) {
            return 1.0
        } else {
            return -1.0
        }
    }
}

class SawWaveGenerator() : WaveGenerator {
    override fun generate(phase: Double): Double {
        return phase / PI - 1.0
    }
}

class WhiteNoiseGenerator() : WaveGenerator {
    override fun generate(phase: Double): Double {
        return Random.nextDouble(-1.0, 1.0)
    }
}
