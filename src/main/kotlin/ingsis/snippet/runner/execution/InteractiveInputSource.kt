package ingsis.snippet.runner.execution

import com.printscript.common.InputSource
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * `readInput` bloqueante: el intérprete se detiene acá hasta que el cliente manda el valor o se
 * agota la espera. Es lo que hace posible la ejecución interactiva (US #10), a diferencia de
 * [ingsis.snippet.runner.facade.QueuedInputSource], que recibe todos los inputs por adelantado.
 *
 * El tiempo que el programa pasa esperando a una persona no puede contar contra el presupuesto de
 * cómputo, así que se acumula aparte en [waitedNanos] y el watchdog lo descuenta.
 */
class InteractiveInputSource(
    private val inputTimeout: Duration,
    private val onPrompt: (String) -> Unit,
    private val onGiveUp: (String) -> Unit = {},
) : InputSource {
    private val pending = LinkedBlockingQueue<String>()
    private val waited = AtomicLong(0)
    private val waitingSince = AtomicLong(0)
    private val cancelled = AtomicBoolean(false)
    private var cancellationReason: String = "Execution cancelled"

    /**
     * Incluye la espera **en curso**: si solo se sumara al volver de `poll`, el watchdog vería
     * crecer el tiempo de cómputo mientras el programa está detenido esperando a una persona y
     * cortaría una sesión que no está consumiendo nada.
     */
    val waitedNanos: Long
        get() {
            val startedAt = waitingSince.get()
            return waited.get() + if (startedAt == 0L) 0 else System.nanoTime() - startedAt
        }

    fun offer(value: String) {
        pending.put(value)
    }

    fun cancel(reason: String) {
        cancellationReason = reason
        cancelled.set(true)
        // Desbloquea al intérprete si está esperando: al despertar ve la cancelación y corta.
        pending.offer("")
    }

    override fun input(prompt: String): String {
        if (cancelled.get()) throw ExecutionCancelledException(cancellationReason)
        onPrompt(prompt)
        val startedAt = System.nanoTime()
        waitingSince.set(startedAt)
        val value =
            try {
                pending.poll(inputTimeout.toMillis(), TimeUnit.MILLISECONDS)
            } finally {
                waitingSince.set(0)
                waited.addAndGet(System.nanoTime() - startedAt)
            }
        if (cancelled.get()) throw ExecutionCancelledException(cancellationReason)
        if (value != null) return value

        // Avisarle a la sesión que esto es terminal: si no, el intérprete sigue con la sentencia
        // siguiente y el usuario recibe la cascada de errores derivados en vez del motivo real.
        val reason = "No input received after ${inputTimeout.toMillis()} ms"
        onGiveUp(reason)
        throw ExecutionCancelledException(reason)
    }
}
