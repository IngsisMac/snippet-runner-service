package ingsis.snippet.runner.facade

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QueuedInputSourceTest {
    private lateinit var inputSource: QueuedInputSource

    @BeforeEach
    fun setUp() {
        inputSource = QueuedInputSource(listOf("first", "second"))
    }

    @Test
    fun shouldDrainInputsInOrder() {
        val first = inputSource.input("a?")
        val second = inputSource.input("b?")

        assertEquals("first", first)
        assertEquals("second", second)
        assertFalse(inputSource.exhausted)
    }

    @Test
    fun shouldRecordUnansweredPromptsOnceInputsRunOut() {
        inputSource.input("a?")
        inputSource.input("b?")

        val third = inputSource.input("c?")

        assertEquals("", third)
        assertTrue(inputSource.exhausted)
        assertEquals(listOf("c?"), inputSource.missingInputs())
    }
}
