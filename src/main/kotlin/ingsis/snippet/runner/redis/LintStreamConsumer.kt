package ingsis.snippet.runner.redis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.runner.facade.PrintScriptFacade
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class LintStreamConsumer(
    private val facade: PrintScriptFacade,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun processRecord(record: MapRecord<String, String, String>) {
        val valueMap = record.value
        val snippetId = valueMap["snippetId"] ?: return
        val content = valueMap["content"] ?: ""
        val version = valueMap["version"] ?: "1.1"
        val rulesVersion = valueMap["rulesVersion"]?.toIntOrNull() ?: 0
        val rules = parseRules(valueMap["rules"] ?: "{}")

        val findings = facade.lint(content, version, rules)
        val status = if (findings.isEmpty()) "COMPLIANT" else "NOT_COMPLIANT"

        emitResult(snippetId, rulesVersion, status, findings.size)
    }

    private fun parseRules(rulesJson: String): Map<String, Any> =
        try {
            objectMapper.readValue(rulesJson, object : TypeReference<Map<String, Any>>() {})
        } catch (_: Exception) {
            emptyMap()
        }

    private fun emitResult(
        snippetId: String,
        rulesVersion: Int,
        status: String,
        findingsCount: Int,
    ) {
        val payload =
            mapOf(
                "snippetId" to snippetId,
                "rulesVersion" to rulesVersion.toString(),
                "status" to status,
                "findingsCount" to findingsCount.toString(),
                "timestamp" to System.currentTimeMillis().toString(),
            )
        val resultRecord =
            StreamRecords
                .newRecord()
                .ofStrings(payload)
                .withStreamKey(RESULTS_STREAM)

        redisTemplate.opsForStream<String, String>().add(resultRecord)
        logger.info("Processed lint for snippet {} with result {} v{}", snippetId, status, rulesVersion)
    }

    companion object {
        const val LINT_REQUEST_STREAM = "lint-requests-stream"
        const val RESULTS_STREAM = "compliance-results-stream"
    }
}
