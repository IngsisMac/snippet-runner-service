package ingsis.snippet.runner.facade

import com.printscript.common.OutputEmitter

class BoundedOutputEmitter(
    private val maxLines: Int = DEFAULT_MAX_LINES,
) : OutputEmitter {
    private val outputList = mutableListOf<String>()
    var limitExceeded: Boolean = false
        private set

    override fun print(message: String) {
        if (outputList.size < maxLines) {
            outputList.add(message)
        } else {
            limitExceeded = true
        }
    }

    fun getOutputs(): List<String> = outputList.toList()

    companion object {
        const val DEFAULT_MAX_LINES = 10_000
    }
}
