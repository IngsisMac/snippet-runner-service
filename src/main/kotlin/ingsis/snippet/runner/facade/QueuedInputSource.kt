package ingsis.snippet.runner.facade

import com.printscript.common.InputSource

class QueuedInputSource(
    inputs: List<String> = emptyList(),
) : InputSource {
    private val queue = ArrayDeque(inputs)

    override fun input(prompt: String): String = queue.removeFirstOrNull() ?: ""
}
