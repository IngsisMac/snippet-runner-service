package ingsis.snippet.runner.redis

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.redis.event.ComplianceResultEvent
import ingsis.snippet.redis.event.LintRequestEvent
import ingsis.snippet.runner.facade.ParseLintFinding
import ingsis.snippet.runner.facade.PrintScriptFacade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StreamOperations

class LintStreamConsumerTest {
    private lateinit var facade: PrintScriptFacade
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var streamOperations: StreamOperations<String, String, Any>
    private lateinit var lintResultProducer: LintResultProducer
    private lateinit var consumer: LintStreamConsumer
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        facade = mock()
        redisTemplate = mock()
        streamOperations = mock()
        whenever(redisTemplate.opsForStream<String, Any>()).thenReturn(streamOperations)
        whenever(streamOperations.add(any())).thenReturn(RecordId.of("100-0"))
        lintResultProducer = LintResultProducer(redisTemplate)
        consumer = LintStreamConsumer(facade, lintResultProducer, objectMapper, redisTemplate)
    }

    @Test
    fun shouldProcessValidRecordAndEmitCompliantResultWhenNoFindings() {
        val event =
            LintRequestEvent(
                snippetId = "snippet-123",
                content = "let x: number = 5;",
                version = "1.1",
                rulesVersion = 2,
                rules = "{\"ruleA\": true}",
            )
        val record: ObjectRecord<String, LintRequestEvent> =
            StreamRecords.newRecord().ofObject(event).withStreamKey("lint-request")
        whenever(facade.lint(event.content, event.version, mapOf("ruleA" to true))).thenReturn(emptyList())

        consumer.onMessage(record)

        verify(facade).lint(event.content, event.version, mapOf("ruleA" to true))
        val captor = argumentCaptor<ObjectRecord<String, ComplianceResultEvent>>()
        verify(streamOperations).add(captor.capture())
        val captured = captor.firstValue.value
        assertEquals("snippet-123", captured.snippetId)
        assertEquals(2, captured.rulesVersion)
        assertEquals("COMPLIANT", captured.status)
        assertEquals(0, captured.findingsCount)
    }

    @Test
    fun shouldProcessRecordAndEmitNotCompliantResultWhenFindingsExist() {
        val event =
            LintRequestEvent(
                snippetId = "snippet-456",
                content = "let MyVar: number = 5;",
                version = "1.1",
                rulesVersion = 3,
                rules = "{}",
            )
        val record: ObjectRecord<String, LintRequestEvent> =
            StreamRecords.newRecord().ofObject(event).withStreamKey("lint-request")
        whenever(facade.lint(any(), any(), any())).thenReturn(listOf(ParseLintFinding("Invalid naming", 1, 5)))

        consumer.onMessage(record)

        verify(facade).lint(event.content, event.version, emptyMap())
        val captor = argumentCaptor<ObjectRecord<String, ComplianceResultEvent>>()
        verify(streamOperations).add(captor.capture())
        val captured = captor.firstValue.value
        assertEquals("snippet-456", captured.snippetId)
        assertEquals(3, captured.rulesVersion)
        assertEquals("NOT_COMPLIANT", captured.status)
        assertEquals(1, captured.findingsCount)
    }

    @Test
    fun shouldRetryUpToThreeTimesAndEmitFailedWhenAllAttemptsFail() {
        val event =
            LintRequestEvent(
                snippetId = "snippet-err",
                content = "invalid code syntax",
                version = "1.1",
                rulesVersion = 1,
                rules = "{}",
            )
        val record: ObjectRecord<String, LintRequestEvent> =
            StreamRecords.newRecord().ofObject(event).withStreamKey("lint-request")
        whenever(facade.lint(any(), any(), any())).thenThrow(RuntimeException("Fatal parser error"))

        consumer.onMessage(record)

        verify(facade, times(3)).lint(event.content, event.version, emptyMap())
        val captor = argumentCaptor<ObjectRecord<String, ComplianceResultEvent>>()
        verify(streamOperations).add(captor.capture())
        val captured = captor.firstValue.value
        assertEquals("snippet-err", captured.snippetId)
        assertEquals(1, captured.rulesVersion)
        assertEquals("FAILED", captured.status)
        assertEquals(0, captured.findingsCount)
    }

    @Test
    fun shouldSucceedOnRetryWhenIntermittentFailureOccurs() {
        val event =
            LintRequestEvent(
                snippetId = "snippet-intermittent",
                content = "let a: number = 1;",
                version = "1.1",
                rulesVersion = 1,
                rules = "{}",
            )
        val record: ObjectRecord<String, LintRequestEvent> =
            StreamRecords.newRecord().ofObject(event).withStreamKey("lint-request")
        whenever(facade.lint(any(), any(), any()))
            .thenThrow(RuntimeException("Temporary hiccup"))
            .thenReturn(emptyList())

        consumer.onMessage(record)

        verify(facade, times(2)).lint(event.content, event.version, emptyMap())
        val captor = argumentCaptor<ObjectRecord<String, ComplianceResultEvent>>()
        verify(streamOperations).add(captor.capture())
        val captured = captor.firstValue.value
        assertEquals("snippet-intermittent", captured.snippetId)
        assertEquals(1, captured.rulesVersion)
        assertEquals("COMPLIANT", captured.status)
        assertEquals(0, captured.findingsCount)
    }

    @Test
    fun shouldFallbackToEmptyRulesWhenJsonMalformed() {
        val event =
            LintRequestEvent(
                snippetId = "snippet-malformed-rules",
                content = "let a: number = 1;",
                version = "1.1",
                rulesVersion = 1,
                rules = "{malformed-json",
            )
        val record: ObjectRecord<String, LintRequestEvent> =
            StreamRecords.newRecord().ofObject(event).withStreamKey("lint-request")
        whenever(facade.lint(any(), any(), any())).thenReturn(emptyList())

        consumer.onMessage(record)

        verify(facade).lint(event.content, event.version, emptyMap())
        val captor = argumentCaptor<ObjectRecord<String, ComplianceResultEvent>>()
        verify(streamOperations).add(captor.capture())
        val captured = captor.firstValue.value
        assertEquals("snippet-malformed-rules", captured.snippetId)
        assertEquals(1, captured.rulesVersion)
        assertEquals("COMPLIANT", captured.status)
        assertEquals(0, captured.findingsCount)
    }

    @Test
    fun shouldConfigureReceiverOptionsCorrectly() {
        val options = consumer.options()

        assertNotNull(options)
        assertEquals(LintRequestEvent::class.java, options.targetType)
    }
}
