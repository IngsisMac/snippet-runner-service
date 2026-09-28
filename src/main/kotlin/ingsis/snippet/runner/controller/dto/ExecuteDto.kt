package ingsis.snippet.runner.controller.dto

data class ExecuteRequest(
    val content: String,
    val version: String = "1.1",
    val inputs: List<String> = emptyList(),
    val env: Map<String, String>? = null,
)

data class ExecuteResponse(
    val outputs: List<String>,
    val errors: List<String>,
    val completed: Boolean,
)
