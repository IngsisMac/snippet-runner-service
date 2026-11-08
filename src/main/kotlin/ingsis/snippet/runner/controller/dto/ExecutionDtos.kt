package ingsis.snippet.runner.controller.dto

import java.util.UUID

data class StartExecutionRequest(
    val content: String,
    val version: String = "1.1",
    val env: Map<String, String> = emptyMap(),
)

data class StartExecutionResponse(
    val executionId: UUID,
)

data class ExecutionInputRequest(
    val value: String = "",
)

data class ExecutionStatusResponse(
    val executionId: UUID,
    val done: Boolean,
)
