package ingsis.snippet.runner.execution

import com.printscript.common.EnvSource
import com.printscript.common.Version
import com.printscript.runner.PrintScriptRunner
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Una ejecución en curso: el hilo que corre el programa, la cola de eventos que consume el cliente
 * por SSE y el canal por el que le llegan los inputs.
 *
 * El estado vive en memoria del runner, que es el servicio que tiene el intérprete. **Límite
 * conocido:** con más de una réplica del runner haría falta afinidad de sesión, porque la sesión
 * solo existe en el proceso que la creó. Hoy hay una sola réplica.
 */
class ExecutionSession(
    val id: UUID,
    private val content: String,
    private val version: String,
    private val env: Map<String, String>,
    private val properties: ExecutionProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Historial y suscriptores se tocan bajo el mismo candado: sin eso, un evento publicado entre
     * que el suscriptor lee el historial y se registra se pierde, y dos eventos concurrentes
     * pueden llegarle al cliente al revés.
     */
    private val lock = Any()
    private val history = mutableListOf<ExecutionEvent>()
    private val subscribers = ConcurrentLinkedQueue<(ExecutionEvent) -> Unit>()
    private val finished = CountDownLatch(1)
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var cancellationReason: String = ""

    private val outputEmitter =
        StreamingOutputEmitter(maxLines = MAX_OUTPUT_LINES, onLine = { publish(ExecutionEvent.Output(it)) })
    private val inputSource =
        InteractiveInputSource(
            inputTimeout = properties.inputTimeout,
            onPrompt = { publish(ExecutionEvent.InputRequest(it)) },
            onGiveUp = { reason -> cancel(reason) },
        )

    @Volatile
    private var startedAtNanos: Long = 0

    val isDone: Boolean
        get() = finished.count == 0L

    @Volatile
    var finishedAtMillis: Long = 0
        private set

    /** Nanosegundos de cómputo, sin contar lo que se esperó en `readInput`. */
    fun computeElapsedNanos(): Long =
        if (startedAtNanos == 0L) 0 else System.nanoTime() - startedAtNanos - inputSource.waitedNanos

    fun run() {
        startedAtNanos = System.nanoTime()
        try {
            val result =
                PrintScriptRunner.execute(
                    source = StringReader(content),
                    version = Version.from(version).getOrElse { Version.V1_1 },
                    output = outputEmitter,
                    input = inputSource,
                    env = EnvSource { key -> env[key] },
                )
            publishCompletion(result.errors.map { "${it.span.start.line}:${it.span.start.column}: ${it.message}" })
        } catch (ex: ExecutionCancelledException) {
            publishCompletion(listOf(ex.reason))
        } catch (
            @Suppress("TooGenericExceptionCaught") ex: Exception,
        ) {
            logger.warn("Execution {} failed: {}", id, ex.message)
            publishCompletion(listOf(ex.message ?: "Execution failed"))
        } finally {
            finishedAtMillis = System.currentTimeMillis()
            finished.countDown()
        }
    }

    fun submitInput(value: String) {
        if (isDone) throw ExecutionAlreadyFinishedException()
        inputSource.offer(value)
    }

    fun cancel(reason: String) {
        if (!cancelled.compareAndSet(false, true)) return
        cancellationReason = reason
        outputEmitter.cancel(reason)
        inputSource.cancel(reason)
    }

    /**
     * Entrega los eventos ya producidos y después los nuevos, hasta que la ejecución termina. Se
     * reproduce lo acumulado porque el cliente arranca la ejecución y recién después se suscribe:
     * sin esto perdería las primeras líneas de un programa rápido, y un cliente que se reconecta
     * no vería nada de lo anterior.
     */
    fun consume(onEvent: (ExecutionEvent) -> Unit) {
        synchronized(lock) {
            history.forEach(onEvent)
            subscribers.add(onEvent)
        }
        finished.await(properties.computeTimeout.seconds + properties.inputTimeout.seconds, TimeUnit.SECONDS)
        subscribers.remove(onEvent)
    }

    private fun publish(event: ExecutionEvent) {
        synchronized(lock) {
            history.add(event)
            subscribers.forEach { it(event) }
        }
    }

    /**
     * `PrintScriptRunner.execute` no aborta ante el error de una sentencia: la registra y sigue
     * con la siguiente. Al cancelar, eso produce una cascada de errores derivados (la variable
     * que no llegó a declararse, y así) que no le dice nada al usuario. Cuando la sesión se
     * canceló, se reporta solo el motivo.
     */
    private fun publishCompletion(errors: List<String>) {
        val allErrors =
            if (cancelled.get()) listOf(cancellationReason) else errors + boundaryViolations()
        publish(ExecutionEvent.Completed(completed = allErrors.isEmpty(), errors = allErrors))
    }

    private fun boundaryViolations(): List<String> =
        if (outputEmitter.limitExceeded) {
            listOf("Output truncated: the program printed more than $MAX_OUTPUT_LINES lines")
        } else {
            emptyList()
        }

    companion object {
        /** Misma cota que la ejecución sincrónica (D2). */
        const val MAX_OUTPUT_LINES = 10_000
    }
}
