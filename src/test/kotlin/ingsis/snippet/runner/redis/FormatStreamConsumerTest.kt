package ingsis.snippet.runner.redis

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.redis.event.FormatRequestEvent
import ingsis.snippet.redis.event.FormatResultEvent
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

class FormatStreamConsumerTest {
    private lateinit var facade: PrintScriptFacade
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var streamOperations: StreamOperations<String, String, Any>
    private lateinit var formatResultProducer: FormatResultProducer
    private lateinit var consumer: FormatStreamConsumer
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        facade = mock()
        redisTemplate = mock()
        streamOperations = mock()
        whenever(redisTemplate.opsForStream<String, Any>()).thenReturn(streamOperations)
        whenever(streamOperations.add(any())).thenReturn(RecordId.of("100-0"))
        formatResultProducer = FormatResultProducer(redisTemplate)
        consumer =
            FormatStreamConsumer(
                facade,
                formatResultProducer,
                objectMapper,
                redisTemplate,
                StreamWorkQueue.direct("format-request"),
            )
    }

    @Test
    fun shouldFormatSuccessfullyAndEmitFormattedContent() {
        val event =
            FormatRequestEvent(
                snippetId = "snippet-123",
                content = "let x=5;",
                version = "1.1",
                rulesVersion = 1,
                rules = "{\"indent\": 4}",
            )
        val record: ObjectRecord<String, FormatRequestEvent> =
            StreamRecords.newRecord().ofObject(event).withStreamKey("format-request")
        whenever(facade.format(event.content, event.version, mapOf("indent" to 4)))
            .thenReturn("let x = 5;\n")

        consumer.onMessage(record)

        verify(facade).format(event.content, event.version, mapOf("indent" to 4))
        val captor = argumentCaptor<ObjectRecord<String, FormatResultEvent>>()
        verify(streamOperations).add(captor.capture())
        val captured = captor.firstValue.value
        assertEquals("snippet-123", captured.snippetId)
        assertEquals(1, captured.rulesVersion)
        assertEquals("let x = 5;\n", captured.formattedContent)
        assertEquals("SUCCESS", captured.status)
    }

    @Test
    fun shouldRetryUpToThreeTimesAndEmitEmptyFormattedContentWhenAllAttemptsFail() {
        val event =
            FormatRequestEvent(
                snippetId = "snippet-err",
                content = "let invalid",
                version = "1.1",
                rulesVersion = 2,
                rules = "{}",
            )
        val record: ObjectRecord<String, FormatRequestEvent> =
            StreamRecords.newRecord().ofObject(event).withStreamKey("format-request")
        whenever(facade.format(any(), any(), any())).thenThrow(RuntimeException("Formatter crash"))

        consumer.onMessage(record)

        verify(facade, times(3)).format(event.content, event.version, emptyMap())
        val captor = argumentCaptor<ObjectRecord<String, FormatResultEvent>>()
        verify(streamOperations).add(captor.capture())
        val captured = captor.firstValue.value
        assertEquals("snippet-err", captured.snippetId)
        assertEquals(2, captured.rulesVersion)
        assertEquals("", captured.formattedContent)
        assertEquals("FAILED", captured.status)
    }

    @Test
    fun shouldSucceedOnRetryWhenIntermittentFailureOccurs() {
        val event =
            FormatRequestEvent(
                snippetId = "snippet-retry",
                content = "let a: number = 1;",
                version = "1.1",
                rulesVersion = 1,
                rules = "{}",
            )
        val record: ObjectRecord<String, FormatRequestEvent> =
            StreamRecords.newRecord().ofObject(event).withStreamKey("format-request")
        whenever(facade.format(any(), any(), any()))
            .thenThrow(RuntimeException("Temporary failure"))
            .thenReturn("let a: number = 1;\n")

        consumer.onMessage(record)

        verify(facade, times(2)).format(event.content, event.version, emptyMap())
        val captor = argumentCaptor<ObjectRecord<String, FormatResultEvent>>()
        verify(streamOperations).add(captor.capture())
        val captured = captor.firstValue.value
        assertEquals("snippet-retry", captured.snippetId)
        assertEquals(1, captured.rulesVersion)
        assertEquals("let a: number = 1;\n", captured.formattedContent)
    }

    @Test
    fun shouldFallbackToEmptyRulesWhenJsonMalformed() {
        val event =
            FormatRequestEvent(
                snippetId = "snippet-broken-rules",
                content = "let b: number = 2;",
                version = "1.1",
                rulesVersion = 1,
                rules = "{bad-json",
            )
        val record: ObjectRecord<String, FormatRequestEvent> =
            StreamRecords.newRecord().ofObject(event).withStreamKey("format-request")
        whenever(facade.format(any(), any(), any())).thenReturn("let b: number = 2;\n")

        consumer.onMessage(record)

        verify(facade).format(event.content, event.version, emptyMap())
        val captor = argumentCaptor<ObjectRecord<String, FormatResultEvent>>()
        verify(streamOperations).add(captor.capture())
        val captured = captor.firstValue.value
        assertEquals("snippet-broken-rules", captured.snippetId)
        assertEquals(1, captured.rulesVersion)
        assertEquals("let b: number = 2;\n", captured.formattedContent)
    }

    @Test
    fun shouldConfigureReceiverOptionsCorrectly() {
        val options = consumer.options()

        assertNotNull(options)
        assertEquals(FormatRequestEvent::class.java, options.targetType)
    }
}
