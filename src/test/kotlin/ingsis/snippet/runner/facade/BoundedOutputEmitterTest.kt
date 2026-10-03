package ingsis.snippet.runner.facade

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BoundedOutputEmitterTest {
    private lateinit var emitter: BoundedOutputEmitter

    @BeforeEach
    fun setUp() {
        emitter = BoundedOutputEmitter(maxLines = 2)
    }

    @Test
    fun shouldKeepEveryLineWhileUnderTheLimit() {
        emitter.print("one")
        emitter.print("two")

        assertEquals(listOf("one", "two"), emitter.getOutputs())
        assertFalse(emitter.limitExceeded)
    }

    @Test
    fun shouldDropExtraLinesAndFlagTheOverflow() {
        emitter.print("one")
        emitter.print("two")
        emitter.print("three")

        assertEquals(listOf("one", "two"), emitter.getOutputs())
        assertTrue(emitter.limitExceeded)
    }
}
