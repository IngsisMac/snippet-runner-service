package ingsis.snippet.runner.controller

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.runner.config.SecurityConfig
import ingsis.snippet.runner.controller.dto.ExecuteRequest
import ingsis.snippet.runner.controller.dto.FormatRequest
import ingsis.snippet.runner.controller.dto.LintRequest
import ingsis.snippet.runner.controller.dto.TestSnippetRequest
import ingsis.snippet.runner.controller.dto.ValidateRequest
import ingsis.snippet.runner.facade.ParseExecutionResult
import ingsis.snippet.runner.facade.ParseLintFinding
import ingsis.snippet.runner.facade.ParseValidationResult
import ingsis.snippet.runner.facade.PrintScriptFacade
import ingsis.snippet.runner.facade.TestCaseExecutionResult
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(InternalRunnerController::class)
@Import(SecurityConfig::class)
class InternalRunnerControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var facade: PrintScriptFacade

    @Test
    fun shouldExecuteEndpoint() {
        val request = ExecuteRequest(content = "println(\"hello\");", version = "1.1")
        val result = ParseExecutionResult(outputs = listOf("hello"), errors = emptyList(), completed = true)

        whenever(facade.execute(eq("println(\"hello\");"), eq("1.1"), any(), anyOrNull())).thenReturn(result)

        mockMvc
            .perform(
                post("/runner/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.outputs[0]").value("hello"))
    }

    @Test
    fun shouldValidateEndpoint() {
        val request = ValidateRequest(content = "let a: number = 5;", version = "1.1")
        val result = ParseValidationResult(valid = true, errors = emptyList())

        whenever(facade.validate("let a: number = 5;", "1.1")).thenReturn(result)

        mockMvc
            .perform(
                post("/runner/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
    }

    @Test
    fun shouldFormatEndpoint() {
        val request = FormatRequest(content = "let a:number=5;", version = "1.1")

        whenever(facade.format(eq("let a:number=5;"), eq("1.1"), any())).thenReturn("let a: number = 5;")

        mockMvc
            .perform(
                post("/runner/format")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.formattedContent").value("let a: number = 5;"))
    }

    @Test
    fun shouldLintEndpoint() {
        val request = LintRequest(content = "let a_b: number = 5;", version = "1.1")
        val findings = listOf(ParseLintFinding(message = "Invalid format", line = 1, column = 5))

        whenever(facade.lint(eq("let a_b: number = 5;"), eq("1.1"), any())).thenReturn(findings)

        mockMvc
            .perform(
                post("/runner/lint")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.findingsCount").value(1))
            .andExpect(jsonPath("$.findings[0].message").value("Invalid format"))
    }

    @Test
    fun shouldTestEndpoint() {
        val request = TestSnippetRequest(content = "println(\"ok\");", version = "1.1", expectedOutputs = listOf("ok"))
        val result =
            TestCaseExecutionResult(
                passed = true,
                actualOutputs = listOf("ok"),
                expectedOutputs = listOf("ok"),
                errors = emptyList()
            )

        whenever(facade.runTest(eq("println(\"ok\");"), eq("1.1"), any(), any(), anyOrNull())).thenReturn(result)

        mockMvc
            .perform(
                post("/runner/test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.passed").value(true))
    }
}
