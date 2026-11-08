package ingsis.snippet.runner.execution

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Cotas de una ejecución interactiva. El presupuesto de cómputo no incluye el tiempo que el
 * programa pasa esperando que una persona escriba: eso tiene su propio límite.
 */
@ConfigurationProperties(prefix = "runner.execution")
data class ExecutionProperties(
    /** Tiempo de cómputo máximo, descontando lo que se esperó en `readInput`. */
    val computeTimeout: Duration = DEFAULT_COMPUTE_TIMEOUT,
    /** Cuánto se espera un `readInput` antes de cortar la sesión. */
    val inputTimeout: Duration = DEFAULT_INPUT_TIMEOUT,
    /** Cuánto sobrevive una sesión terminada antes de limpiarse. */
    val retention: Duration = DEFAULT_RETENTION,
    /** Sesiones simultáneas que el runner acepta. */
    val maxConcurrentSessions: Int = DEFAULT_MAX_CONCURRENT_SESSIONS,
) {
    companion object {
        val DEFAULT_COMPUTE_TIMEOUT: Duration = Duration.ofSeconds(10)
        val DEFAULT_INPUT_TIMEOUT: Duration = Duration.ofSeconds(60)
        val DEFAULT_RETENTION: Duration = Duration.ofMinutes(5)
        const val DEFAULT_MAX_CONCURRENT_SESSIONS = 50
    }
}
