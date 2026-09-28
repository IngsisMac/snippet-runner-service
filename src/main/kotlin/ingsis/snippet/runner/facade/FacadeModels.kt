package ingsis.snippet.runner.facade

data class ParseExecutionResult(
    val outputs: List<String>,
    val errors: List<String>,
    val completed: Boolean,
)

data class ParseValidationResult(
    val valid: Boolean,
    val errors: List<String>,
)

data class ParseLintFinding(
    val message: String,
    val line: Int,
    val column: Int,
)

data class TestCaseExecutionResult(
    val passed: Boolean,
    val actualOutputs: List<String>,
    val expectedOutputs: List<String>,
    val errors: List<String>,
)
