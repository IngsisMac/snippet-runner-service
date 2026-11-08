package ingsis.snippet.runner.redis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.redis.event.FormatRequestEvent
import ingsis.snippet.redis.event.FormatResultEvent
import ingsis.snippet.runner.facade.PrintScriptFacade
import org.austral.ingsis.redis.RedisStreamConsumer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.stream.StreamReceiver
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@Profile("!test")
class FormatStreamConsumer(
    private val facade: PrintScriptFacade,
    private val formatResultProducer: FormatResultProducer,
    private val objectMapper: ObjectMapper,
    redisTemplate: RedisTemplate<String, String>,
    private val work: StreamWorkQueue = StreamWorkQueue("format-request"),
) : RedisStreamConsumer<FormatRequestEvent>(
        streamKey = "format-request",
        groupId = "runner-format-group",
        redis = redisTemplate,
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    public override fun options(): StreamReceiver.StreamReceiverOptions<
        String,
        ObjectRecord<String, FormatRequestEvent>,
    > =
        StreamReceiver.StreamReceiverOptions
            .builder()
            .pollTimeout(Duration.ofMillis(POLL_TIMEOUT_MS))
            .targetType(FormatRequestEvent::class.java)
            .build()

    public override fun onMessage(record: ObjectRecord<String, FormatRequestEvent>) {
        val event = record.value
        work.submit {
            val cid =
                event.correlationId.ifBlank {
                    java.util.UUID
                        .randomUUID()
                        .toString()
                }
            org.slf4j.MDC.put(ingsis.snippet.runner.tracing.CorrelationIdFilter.CORRELATION_ID_KEY, cid)
            try {
                process(event)
            } finally {
                org.slf4j.MDC.remove(ingsis.snippet.runner.tracing.CorrelationIdFilter.CORRELATION_ID_KEY)
            }
        }
    }

    private fun process(event: FormatRequestEvent) {
        val rulesMap = parseRules(event.rules)
        val formatted = formatWithRetry(event, rulesMap)

        if (formatted == null) {
            logger.error("Format failed after $MAX_ATTEMPTS attempts for snippet {}", event.snippetId)
            emitResult(event, formattedContent = "", status = "FAILED")
        } else {
            emitResult(event, formattedContent = formatted, status = "SUCCESS")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun formatWithRetry(
        event: FormatRequestEvent,
        rules: Map<String, Any>,
    ): String? {
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return facade.format(event.content, event.version, rules)
            } catch (ex: Exception) {
                logger.warn("Format attempt $attempt failed for snippet {}: {}", event.snippetId, ex.message)
            }
        }
        return null
    }

    private fun emitResult(
        event: FormatRequestEvent,
        formattedContent: String,
        status: String,
    ) {
        formatResultProducer.emit(
            FormatResultEvent(
                snippetId = event.snippetId,
                rulesVersion = event.rulesVersion,
                formattedContent = formattedContent,
                status = status,
                correlationId =
                    org.slf4j.MDC.get(ingsis.snippet.runner.tracing.CorrelationIdFilter.CORRELATION_ID_KEY)
                        ?: event.correlationId,
            ),
        )
        logger.info("Processed format for snippet {} v{} with status {}", event.snippetId, event.rulesVersion, status)
    }

    private fun parseRules(rulesJson: String): Map<String, Any> =
        try {
            objectMapper.readValue(rulesJson, object : TypeReference<Map<String, Any>>() {})
        } catch (_: Exception) {
            emptyMap()
        }

    companion object {
        private const val POLL_TIMEOUT_MS = 10000L
        private const val MAX_ATTEMPTS = 3
    }
}
