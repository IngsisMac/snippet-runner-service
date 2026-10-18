package ingsis.snippet.runner.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * El corte por tiempo es del runner, no del cliente: antes la única cota era el `read-timeout`
 * del `RestClient` de `snippet-service`, así que al vencer el cliente se rendía pero el programa
 * seguía corriendo acá adentro.
 */
class ExecutionSessionRegistryTest {
    private lateinit var registry: ExecutionSessionRegistry

    @BeforeEach
    fun setUp() {
        registry = ExecutionSessionRegistry(propertiesWith(inputTimeout = Duration.ofSeconds(30)))
    }

    @Test
    fun shouldHandOutASessionThatRunsTheProgram() {
        val session = registry.start("println(7);", "1.1", emptyMap())

        val events = collectFrom(session)

        assertEquals(listOf("7"), events.filterIsInstance<ExecutionEvent.Output>().map { it.line })
        assertEquals(session.id, registry.get(session.id).id)
    }

    @Test
    fun shouldCancelASessionThatOutlivesItsComputeBudget() {
        registry = ExecutionSessionRegistry(propertiesWith(computeTimeout = Duration.ofMillis(20)))
        // PrintScript 1.1 no tiene bucles, así que la única forma de consumir tiempo de cómputo
        // es un programa largo: este tarda ~180 ms, muy por encima del presupuesto de 20 ms.
        val program = (1..LONG_PROGRAM_STATEMENTS).joinToString("\n") { "println($it);" }
        val session = registry.start(program, "1.1", emptyMap())
        val events = CopyOnWriteArrayList<ExecutionEvent>()
        val finished = CountDownLatch(1)
        Thread {
            session.consume {
                events.add(it)
                if (it is ExecutionEvent.Completed) finished.countDown()
            }
        }.start()

        val watchdog = sweepUntil(finished)

        assertTrue(finished.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the watchdog never cut the execution")
        watchdog.join()
        val end = events.filterIsInstance<ExecutionEvent.Completed>().single()
        assertFalse(end.completed)
        assertTrue(end.errors.single().contains("compute limit"), end.errors.toString())
        assertTrue(
            events.count { it is ExecutionEvent.Output } < LONG_PROGRAM_STATEMENTS,
            "the program ran to completion despite being cancelled",
        )
    }

    @Test
    fun shouldNotCancelASessionThatIsOnlyWaitingForAPerson() {
        registry =
            ExecutionSessionRegistry(
                propertiesWith(computeTimeout = Duration.ofMillis(200), inputTimeout = Duration.ofSeconds(30)),
            )
        val session = registry.start("""let name: string = readInput("?");""", "1.1", emptyMap())
        val asked = CountDownLatch(1)
        Thread { session.consume { if (it is ExecutionEvent.InputRequest) asked.countDown() } }.start()
        assertTrue(asked.await(AWAIT_SECONDS, TimeUnit.SECONDS))

        Thread.sleep(SETTLE_MILLIS)
        registry.sweep()

        assertFalse(session.isDone, "the watchdog cut an execution that was only waiting for input")
    }

    @Test
    fun shouldForgetSessionsThatFinishedLongAgo() {
        registry = ExecutionSessionRegistry(propertiesWith(retention = Duration.ZERO))
        val session = registry.start("println(1);", "1.1", emptyMap())
        collectFrom(session)

        Thread.sleep(SETTLE_MILLIS)
        registry.sweep()

        assertEquals(
            HttpStatus.NOT_FOUND,
            assertThrows<ResponseStatusException> { registry.get(session.id) }.statusCode
        )
    }

    @Test
    fun shouldRefuseANewSessionOnceTheLimitIsReached() {
        registry =
            ExecutionSessionRegistry(
                propertiesWith(maxConcurrentSessions = 1, inputTimeout = Duration.ofSeconds(30)),
            )
        registry.start("""let name: string = readInput("?");""", "1.1", emptyMap())

        val exception =
            assertThrows<ResponseStatusException> { registry.start("println(1);", "1.1", emptyMap()) }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.statusCode)
    }

    @Test
    fun shouldAnswerNotFoundForAnExecutionThatDoesNotExist() {
        val exception = assertThrows<ResponseStatusException> { registry.get(UUID.randomUUID()) }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    private fun propertiesWith(
        computeTimeout: Duration = Duration.ofSeconds(5),
        inputTimeout: Duration = Duration.ofSeconds(2),
        retention: Duration = Duration.ofMinutes(5),
        maxConcurrentSessions: Int = 50,
    ) = ExecutionProperties(computeTimeout, inputTimeout, retention, maxConcurrentSessions)

    /** Corre el watchdog como lo haría el scheduler, hasta que la sesión termina. */
    private fun sweepUntil(finished: CountDownLatch): Thread =
        Thread {
            while (!finished.await(SWEEP_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)) {
                registry.sweep()
            }
        }.also { it.start() }

    private fun collectFrom(session: ExecutionSession): List<ExecutionEvent> {
        val events = CopyOnWriteArrayList<ExecutionEvent>()
        session.consume { events.add(it) }
        return events.toList()
    }

    companion object {
        private const val AWAIT_SECONDS = 3L
        private const val SETTLE_MILLIS = 150L
        private const val SWEEP_INTERVAL_MILLIS = 2L
        private const val LONG_PROGRAM_STATEMENTS = 20_000
    }
}
