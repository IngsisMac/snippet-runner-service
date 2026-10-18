package ingsis.snippet.runner.execution

/**
 * Lo que una sesión de ejecución le va contando al cliente mientras corre (US #9 y #10).
 * Viaja como eventos SSE: el campo `type` es el nombre del evento y el resto, su payload.
 */
sealed interface ExecutionEvent {
    val type: String

    /** Una línea que el programa imprimió. */
    data class Output(
        val line: String,
    ) : ExecutionEvent {
        override val type = "output"
    }

    /** El programa llamó a `readInput` y está esperando: hasta que no llegue, no avanza. */
    data class InputRequest(
        val prompt: String,
    ) : ExecutionEvent {
        override val type = "input-request"
    }

    /** La ejecución terminó, con o sin errores. No hay más eventos después de este. */
    data class Completed(
        val completed: Boolean,
        val errors: List<String>,
    ) : ExecutionEvent {
        override val type = "end"
    }
}

/** Corta la ejecución desde adentro cuando la sesión se cancela o se queda sin tiempo. */
class ExecutionCancelledException(
    val reason: String,
) : RuntimeException(reason)
