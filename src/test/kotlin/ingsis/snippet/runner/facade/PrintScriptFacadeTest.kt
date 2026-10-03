package ingsis.snippet.runner.facade

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PrintScriptFacadeTest {
    private lateinit var facade: PrintScriptFacade

    @BeforeEach
    fun setUp() {
        facade = PrintScriptFacade()
    }

    @Test
    fun shouldExecutePrintScriptSuccessfully() {
        val code = "println(\"Hello World\");"
        val result = facade.execute(code, "1.1")

        assertTrue(result.completed)
        assertEquals(listOf("Hello World"), result.outputs)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun shouldExecutePrintScriptWithInputs() {
        val code =
            """
            let name: string = readInput("Enter name: ");
            println("Hello " + name);
            """.trimIndent()
        val result = facade.execute(code, "1.1", inputs = listOf("Ingsis"))

        assertTrue(result.completed)
        assertEquals(listOf("Enter name: ", "Hello Ingsis"), result.outputs)
    }

    @Test
    fun shouldValidateValidCode() {
        val code = "let x: number = 42; println(x);"
        val result = facade.validate(code, "1.1")

        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun shouldValidateInvalidCode() {
        val code = "let x: number = ;"
        val result = facade.validate(code, "1.1")

        assertFalse(result.valid)
        assertFalse(result.errors.isEmpty())
    }

    @Test
    fun shouldFormatCodeWithRules() {
        val code = "let x: number = 42;"
        val formatted = facade.format(code, "1.1", mapOf("enforce-spacing-before-colon-in-declaration" to true))

        assertTrue(formatted.contains("let x : number = 42;"))
    }

    @Test
    fun shouldLintCodeAndReportFindings() {
        val code = "let my_var: number = 10;"
        val findings = facade.lint(code, "1.1", mapOf("identifier_format" to "camelCase"))

        assertFalse(findings.isEmpty())
        assertTrue(findings.any { it.message.contains("my_var") || it.message.contains("camelCase") })
    }

    @Test
    fun shouldRunPassingTestCase() {
        val code =
            """
            let a: number = 10;
            let b: number = 20;
            println(a + b);
            """.trimIndent()

        val result = facade.runTest(code, "1.1", expectedOutputs = listOf("30"))

        assertTrue(result.passed)
        assertEquals(listOf("30"), result.actualOutputs)
    }

    @Test
    fun shouldRunFailingTestCaseOnOutputMismatch() {
        val code = "println(\"Actual\");"

        val result = facade.runTest(code, "1.1", expectedOutputs = listOf("Expected"))

        assertFalse(result.passed)
        assertEquals(listOf("Actual"), result.actualOutputs)
        assertEquals(listOf("Expected"), result.expectedOutputs)
    }

    @Test
    fun shouldMarkExecutionIncompleteWhenProgramRequestsMissingInput() {
        val code =
            """
            let name: string = readInput("Enter name: ");
            println("Hello " + name);
            """.trimIndent()

        val result = facade.execute(code, "1.1", inputs = emptyList())

        assertFalse(result.completed)
        assertTrue(result.errors.any { it.contains("more inputs than provided") })
    }

    @Test
    fun shouldFailTestCaseWhenInputsAreMissingEvenIfOutputsMatch() {
        val code =
            """
            let name: string = readInput("Enter name: ");
            println("Hello " + name);
            """.trimIndent()

        val result =
            facade.runTest(
                code,
                "1.1",
                inputs = emptyList(),
                expectedOutputs = listOf("Enter name: ", "Hello ")
            )

        assertFalse(result.passed)
        assertEquals(listOf("Enter name: ", "Hello "), result.actualOutputs)
    }
}
