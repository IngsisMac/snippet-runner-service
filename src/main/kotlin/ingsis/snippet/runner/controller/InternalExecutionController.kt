package ingsis.snippet.runner.controller

import ingsis.snippet.runner.controller.dto.ExecutionInputRequest
import ingsis.snippet.runner.controller.dto.ExecutionStatusResponse
import ingsis.snippet.runner.controller.dto.StartExecutionRequest
import ingsis.snippet.runner.controller.dto.StartExecutionResponse
import ingsis.snippet.runner.execution.ExecutionSessionRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Ejecución interactiva (US #9 y #10). El cliente arranca una sesión, se suscribe al stream de
 * salida y manda los inputs que el programa va pidiendo.
 *
 * Se eligió SSE sobre HTTP plano en vez de WebSocket: reusa el mismo JWT y el mismo gateway que
 * el resto de la API, y `SseEmitter` es nativo de Spring MVC. Lo que SSE no da —un canal de
 * vuelta— lo cubre el `POST` de input, que es una request más.
 */
@RestController
@RequestMapping("/runner/executions")
class InternalExecutionController(
    private val registry: ExecutionSessionRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val streamExecutor =
        Executors.newCachedThreadPool { runnable -> Thread(runnable, "execution-stream").apply { isDaemon = true } }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun start(
        @RequestBody request: StartExecutionRequest,
    ): StartExecutionResponse {
        val session = registry.start(request.content, request.version, request.env)
        return StartExecutionResponse(session.id)
    }

    /**
     * Existencia de la sesión. `snippet-service` lo consulta antes de abrir el stream: una vez que
     * empezó a mandar bytes ya no puede cambiar el código de respuesta, así que sin esto un
     * `executionId` inexistente terminaba en `500` en lugar de `404`.
     */
    @GetMapping("/{executionId}")
    fun status(
        @PathVariable executionId: UUID,
    ): ExecutionStatusResponse = registry.get(executionId).let { ExecutionStatusResponse(it.id, it.isDone) }

    /**
     * `timeout = 0` deja que el corte lo decida la sesión y no el contenedor de servlets: el
     * watchdog del runner ya garantiza que ninguna ejecución quede colgada para siempre.
     */
    @GetMapping("/{executionId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @PathVariable executionId: UUID,
    ): SseEmitter {
        val session = registry.get(executionId)
        val emitter = SseEmitter(0L)
        streamExecutor.execute {
            try {
                session.consume { event -> emitter.send(SseEmitter.event().name(event.type).data(event)) }
                emitter.complete()
            } catch (
                @Suppress("TooGenericExceptionCaught") ex: Exception,
            ) {
                logger.debug("Stream of execution {} ended: {}", executionId, ex.message)
                emitter.completeWithError(ex)
            }
        }
        return emitter
    }

    @PostMapping("/{executionId}/input")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submitInput(
        @PathVariable executionId: UUID,
        @RequestBody request: ExecutionInputRequest,
    ) {
        registry.get(executionId).submitInput(request.value)
    }

    @DeleteMapping("/{executionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(
        @PathVariable executionId: UUID,
    ) {
        registry.cancel(executionId, "Execution cancelled by the user")
    }
}
