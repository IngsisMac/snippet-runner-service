package ingsis.snippet.runner.execution

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Sesiones de ejecución vivas, con su hilo y su watchdog.
 *
 * El corte por tiempo es **del runner**, no del cliente: antes la única cota era el `read-timeout`
 * de 5 s del `RestClient` de `snippet-service`, así que al vencer el cliente se rendía pero el
 * programa seguía corriendo acá adentro, ocupando un hilo. Ahora el que corta es el que ejecuta.
 *
 * La cancelación es cooperativa: se marca la sesión y el intérprete se desarma en el próximo
 * `print` o `readInput`. En PrintScript 1.1 no hay bucles, así que todo programa llega a uno de
 * los dos o termina solo; un `Thread.stop` dejaría el intérprete en un estado indefinido.
 */
@Component
class ExecutionSessionRegistry(
    private val properties: ExecutionProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<UUID, ExecutionSession>()
    private val executor =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "execution").apply {
                isDaemon =
                    true
            }
        }

    fun start(
        content: String,
        version: String,
        env: Map<String, String>,
    ): ExecutionSession {
        if (sessions.size >= properties.maxConcurrentSessions) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many executions in progress")
        }
        val session = ExecutionSession(UUID.randomUUID(), content, version, env, properties)
        sessions[session.id] = session
        executor.execute(session::run)
        return session
    }

    fun get(id: UUID): ExecutionSession =
        sessions[id] ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found")

    fun cancel(
        id: UUID,
        reason: String,
    ) {
        get(id).cancel(reason)
    }

    /** Corta las que se pasaron del presupuesto de cómputo y limpia las terminadas hace rato. */
    @Scheduled(fixedDelayString = "\${runner.execution.watchdog-interval:PT1S}")
    fun sweep() {
        val computeLimitNanos = properties.computeTimeout.toNanos()
        val retentionMillis = properties.retention.toMillis()
        val now = System.currentTimeMillis()

        sessions.values.forEach { session ->
            if (!session.isDone && session.computeElapsedNanos() > computeLimitNanos) {
                logger.warn("Execution {} exceeded its compute budget; cancelling", session.id)
                session.cancel("Execution exceeded the ${properties.computeTimeout.toMillis()} ms compute limit")
            }
        }
        sessions.entries.removeIf { (_, session) ->
            session.isDone && now - session.finishedAtMillis > retentionMillis
        }
    }
}
