package ingsis.snippet.runner.execution

import com.printscript.common.OutputEmitter
import ingsis.snippet.runner.facade.BoundedOutputEmitter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Emite cada línea apenas el programa la imprime, en lugar de juntarlas todas y devolverlas al
 * final (US #9). Mantiene la misma cota de líneas que [BoundedOutputEmitter]: pasada esa cantidad
 * deja de emitir y marca la corrida como incompleta.
 *
 * También es el punto donde se nota una cancelación: si el programa sigue imprimiendo después de
 * que la sesión se cortó, la próxima línea lanza y desarma el intérprete.
 */
class StreamingOutputEmitter(
    private val maxLines: Int = BoundedOutputEmitter.DEFAULT_MAX_LINES,
    private val onLine: (String) -> Unit,
) : OutputEmitter {
    private var emitted = 0
    private val cancelled = AtomicBoolean(false)
    private var cancellationReason: String = "Execution cancelled"

    var limitExceeded: Boolean = false
        private set

    fun cancel(reason: String) {
        cancellationReason = reason
        cancelled.set(true)
    }

    override fun print(message: String) {
        if (cancelled.get()) throw ExecutionCancelledException(cancellationReason)
        if (emitted >= maxLines) {
            limitExceeded = true
            return
        }
        emitted++
        onLine(message)
    }
}
