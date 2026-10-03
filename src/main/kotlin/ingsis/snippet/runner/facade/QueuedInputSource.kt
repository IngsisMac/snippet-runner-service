package ingsis.snippet.runner.facade

import com.printscript.common.InputSource

/**
 * Entrega los inputs provistos por adelantado, en orden. Cuando el programa pide más de
 * los que había, devuelve cadena vacía para que la ejecución pueda terminar, pero deja
 * registrado cada prompt sin respuesta para que la fachada marque la corrida como
 * incompleta en lugar de dar por válido un resultado que dependió de un input inventado.
 */
class QueuedInputSource(
    inputs: List<String> = emptyList(),
) : InputSource {
    private val queue = ArrayDeque(inputs)
    private val unansweredPrompts = mutableListOf<String>()

    val exhausted: Boolean
        get() = unansweredPrompts.isNotEmpty()

    fun missingInputs(): List<String> = unansweredPrompts.toList()

    override fun input(prompt: String): String =
        queue.removeFirstOrNull() ?: run {
            unansweredPrompts.add(prompt)
            ""
        }
}
