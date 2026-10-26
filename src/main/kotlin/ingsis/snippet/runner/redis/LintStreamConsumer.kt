package ingsis.snippet.runner.redis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.redis.event.ComplianceResultEvent
import ingsis.snippet.redis.event.LintRequestEvent
import ingsis.snippet.runner.facade.ParseLintFinding
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
class LintStreamConsumer(
    private val facade: PrintScriptFacade,
    private val lintResultProducer: LintResultProducer,
    private val objectMapper: ObjectMapper,
    redisTemplate: RedisTemplate<String, String>,
    private val work: StreamWorkQueue = StreamWorkQueue("lint-request"),
) : RedisStreamConsumer<LintRequestEvent>(
        streamKey = "lint-request",
        groupId = "runner-lint-group",
        redis = redisTemplate,
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    public override fun options(): StreamReceiver.StreamReceiverOptions<
        String,
        ObjectRecord<String, LintRequestEvent>,
    > =
        StreamReceiver.StreamReceiverOptions
            .builder()
            .pollTimeout(Duration.ofMillis(POLL_TIMEOUT_MS))
            .targetType(LintRequestEvent::class.java)
            .build()

    public override fun onMessage(record: ObjectRecord<String, LintRequestEvent>) {
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

    private fun process(event: LintRequestEvent) {
        val rulesMap = parseRules(event.rules)
        val findings = lintWithRetry(event, rulesMap)

        val status =
            when {
                findings == null -> "FAILED"
                findings.isEmpty() -> "COMPLIANT"
                else -> "NOT_COMPLIANT"
            }

        emitResult(event, status, findings.orEmpty())
    }

    @Suppress("TooGenericExceptionCaught")
    private fun lintWithRetry(
        event: LintRequestEvent,
        rules: Map<String, Any>,
    ): List<ParseLintFinding>? {
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return facade.lint(event.content, event.version, rules)
            } catch (ex: Exception) {
                logger.warn("Lint attempt $attempt failed for snippet {}: {}", event.snippetId, ex.message)
            }
        }
        return null
    }

    private fun emitResult(
        event: LintRequestEvent,
        status: String,
        findings: List<ParseLintFinding>,
    ) {
        lintResultProducer.emit(
            ComplianceResultEvent(
                snippetId = event.snippetId,
                rulesVersion = event.rulesVersion,
                status = status,
                findingsCount = findings.size,
                findings = objectMapper.writeValueAsString(findings),
                correlationId =
                    org.slf4j.MDC.get(ingsis.snippet.runner.tracing.CorrelationIdFilter.CORRELATION_ID_KEY)
                        ?: event.correlationId,
            ),
        )
        logger.info("Processed lint for snippet {} with result {} v{}", event.snippetId, status, event.rulesVersion)
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
