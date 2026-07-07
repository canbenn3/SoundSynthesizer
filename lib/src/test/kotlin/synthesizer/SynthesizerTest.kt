package synthesizer

import kotlin.test.Test
import kotlin.test.assertTrue

class SynthesizerTest {
    @Test fun someLibraryMethodReturnsTrue() {
        val classUnderTest = Synthesizer()
        assertTrue(classUnderTest.someLibraryMethod(), "someLibraryMethod should return 'true'")
    }

    @Test fun fakeMainMethod() {
        val classUnderTest = Synthesizer()
        classUnderTest.run()
    }
}
