package ingsis.snippet.runner.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * US #9 y #10: la salida sale a medida que el programa la produce, `readInput` espera de verdad a
 * que llegue el valor, y el corte por tiempo lo decide el runner y no el cliente.
 */
class ExecutionSessionTest {
    private lateinit var properties: ExecutionProperties
    private lateinit var executor: java.util.concurrent.ExecutorService

    @BeforeEach
    fun setUp() {
        properties =
            ExecutionProperties(
                computeTimeout = Duration.ofSeconds(2),
                inputTimeout = Duration.ofSeconds(2),
            )
        executor = Executors.newCachedThreadPool()
    }

    @Test
    fun shouldEmitEveryPrintedLineAndThenAnEndEvent() {
        val session = sessionFor("println(1);\nprintln(2);")

        val events = runAndCollect(session)

        assertEquals(listOf("1", "2"), events.filterIsInstance<ExecutionEvent.Output>().map { it.line })
        assertTrue(events.last().let { it is ExecutionEvent.Completed && it.completed })
    }

    @Test
    fun shouldAskForInputAndResumeOnlyWhenTheValueArrives() {
        val session = sessionFor("""let name: string = readInput("Your name: ");${'\n'}println(name);""")
        val events = CopyOnWriteArrayList<ExecutionEvent>()
        val asked = CountDownLatch(1)

        executor.execute {
            session.consume { event ->
                events.add(event)
                if (event is ExecutionEvent.InputRequest) asked.countDown()
            }
        }
        executor.execute(session::run)

        assertTrue(asked.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the program never asked for input")
        // PrintScript imprime el prompt por el emitter antes de pedir el valor, así que lo único
        // que puede haber salido hasta acá es ese prompt: el println de después todavía no corrió.
        assertEquals(listOf("Your name: "), outputsOf(events))
        session.submitInput("Macky")
        waitUntilDone(session)

        assertEquals(listOf("Your name: ", "Macky"), outputsOf(events))
    }

    @Test
    fun shouldReplayWhatAlreadyHappenedToAClientThatSubscribesLate() {
        val session = sessionFor("println(1);")
        session.run()

        val events = CopyOnWriteArrayList<ExecutionEvent>()
        session.consume { events.add(it) }

        assertEquals(listOf("1"), outputsOf(events))
        assertTrue(events.any { it is ExecutionEvent.Completed })
    }

    @Test
    fun shouldEndWithAnErrorWhenNoInputArrivesBeforeTheDeadline() {
        properties = ExecutionProperties(computeTimeout = Duration.ofSeconds(5), inputTimeout = Duration.ofSeconds(1))
        val session = sessionFor("""let name: string = readInput("Your name: ");""")

        val events = runAndCollect(session)

        val end = events.filterIsInstance<ExecutionEvent.Completed>().single()
        assertFalse(end.completed)
        assertTrue(end.errors.single().contains("No input received"), end.errors.toString())
    }

    @Test
    fun shouldStopTheProgramWhenTheSessionIsCancelled() {
        val session = sessionFor("""let name: string = readInput("Your name: ");${'\n'}println(name);""")
        val events = CopyOnWriteArrayList<ExecutionEvent>()
        val asked = CountDownLatch(1)

        executor.execute {
            session.consume { event ->
                events.add(event)
                if (event is ExecutionEvent.InputRequest) asked.countDown()
            }
        }
        executor.execute(session::run)
        assertTrue(asked.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        session.cancel("Execution cancelled by the user")
        waitUntilDone(session)

        val end = events.filterIsInstance<ExecutionEvent.Completed>().single()
        assertFalse(end.completed)
        assertEquals("Execution cancelled by the user", end.errors.single())
        assertEquals(listOf("Your name: "), outputsOf(events), "it kept running after being cancelled")
    }

    @Test
    fun shouldNotChargeTheTimeSpentWaitingForAPersonAgainstTheComputeBudget() {
        val session = sessionFor("""let name: string = readInput("Your name: ");${'\n'}println(name);""")
        val asked = CountDownLatch(1)
        executor.execute { session.consume { if (it is ExecutionEvent.InputRequest) asked.countDown() } }
        executor.execute(session::run)
        assertTrue(asked.await(AWAIT_SECONDS, TimeUnit.SECONDS))

        Thread.sleep(SLEEP_WHILE_WAITING_MILLIS)
        val computeWhileIdle = session.computeElapsedNanos()
        session.submitInput("Macky")
        waitUntilDone(session)

        assertTrue(
            computeWhileIdle < Duration.ofMillis(SLEEP_WHILE_WAITING_MILLIS).toNanos(),
            "the wait for input was charged as compute time",
        )
    }

    private fun sessionFor(content: String) =
        ExecutionSession(UUID.randomUUID(), content, "1.1", emptyMap(), properties)

    private fun runAndCollect(session: ExecutionSession): List<ExecutionEvent> {
        val events = CopyOnWriteArrayList<ExecutionEvent>()
        executor.execute { session.consume { events.add(it) } }
        session.run()
        waitUntilDone(session)
        return events.toList()
    }

    @Test
    fun shouldRejectInputWhenExecutionIsAlreadyFinished() {
        val session = sessionFor("println(1);")
        session.run()
        assertTrue(session.isDone)

        org.junit.jupiter.api.assertThrows<ExecutionAlreadyFinishedException> {
            session.submitInput("late input")
        }
    }

    private fun outputsOf(events: List<ExecutionEvent>) =
        events.filterIsInstance<ExecutionEvent.Output>().map { it.line }

    private fun waitUntilDone(session: ExecutionSession) {
        val deadline = System.currentTimeMillis() + DONE_TIMEOUT_MILLIS
        while (!session.isDone && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MILLIS)
        }
        Thread.sleep(POLL_MILLIS)
        assertTrue(session.isDone, "the execution never finished")
    }

    companion object {
        private const val AWAIT_SECONDS = 3L
        private const val DONE_TIMEOUT_MILLIS = 5_000L
        private const val POLL_MILLIS = 20L
        private const val SLEEP_WHILE_WAITING_MILLIS = 400L
    }
}
