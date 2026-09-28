package ingsis.snippet.runner.redis

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.runner.facade.ParseLintFinding
import ingsis.snippet.runner.facade.PrintScriptFacade
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate

class LintStreamConsumerTest {
    private lateinit var facade: PrintScriptFacade
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var streamOperations: StreamOperations<String, String, String>
    private lateinit var consumer: LintStreamConsumer
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        facade = mock()
        redisTemplate = mock()
        streamOperations = mock()
        whenever(redisTemplate.opsForStream<String, String>()).thenReturn(streamOperations)
        whenever(streamOperations.add(any())).thenReturn(RecordId.of("100-0"))
        consumer = LintStreamConsumer(facade, redisTemplate, objectMapper)
    }

    @Test
    fun shouldProcessValidRecordAndEmitCompliantResult() {
        val payload =
            mapOf(
                "snippetId" to "snippet-123",
                "content" to "let x: number = 5;",
                "version" to "1.1",
                "rulesVersion" to "2",
                "rules" to "{}",
            )
        val record = MapRecord.create("lint-requests-stream", payload)
        whenever(facade.lint(any(), any(), any())).thenReturn(emptyList())

        consumer.processRecord(record)

        verify(facade).lint("let x: number = 5;", "1.1", emptyMap())
        verify(streamOperations).add(any())
    }

    @Test
    fun shouldProcessRecordWithFindingsAndEmitNotCompliantResult() {
        val payload =
            mapOf(
                "snippetId" to "snippet-456",
                "content" to "let MyVar: number = 5;",
                "version" to "1.1",
                "rulesVersion" to "3",
                "rules" to "{}",
            )
        val record = MapRecord.create("lint-requests-stream", payload)
        whenever(facade.lint(any(), any(), any())).thenReturn(listOf(ParseLintFinding("Invalid naming", 1, 5)))

        consumer.processRecord(record)

        verify(facade).lint("let MyVar: number = 5;", "1.1", emptyMap())
        verify(streamOperations).add(any())
    }

    @Test
    fun shouldIgnoreRecordWithoutSnippetId() {
        val payload = mapOf("content" to "empty")
        val record = MapRecord.create("lint-requests-stream", payload)

        consumer.processRecord(record)
    }
}
