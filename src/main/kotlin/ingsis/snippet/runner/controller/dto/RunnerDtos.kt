package ingsis.snippet.runner.controller.dto

data class ValidateRequest(
    val content: String,
    val version: String = "1.1",
)

data class ValidateResponse(
    val valid: Boolean,
    val errors: List<String>,
)

data class FormatRequest(
    val content: String,
    val version: String = "1.1",
    val rules: Map<String, Any> = emptyMap(),
)

data class FormatResponse(
    val formattedContent: String,
)

data class LintFindingDto(
    val message: String,
    val line: Int,
    val column: Int,
)

data class LintRequest(
    val content: String,
    val version: String = "1.1",
    val rules: Map<String, Any> = emptyMap(),
)

data class LintResponse(
    val findings: List<LintFindingDto>,
    val findingsCount: Int,
)

data class TestSnippetRequest(
    val content: String,
    val version: String = "1.1",
    val inputs: List<String> = emptyList(),
    val expectedOutputs: List<String> = emptyList(),
    val env: Map<String, String>? = null,
)

data class TestSnippetResponse(
    val passed: Boolean,
    val actualOutputs: List<String>,
    val expectedOutputs: List<String>,
    val errors: List<String>,
)
