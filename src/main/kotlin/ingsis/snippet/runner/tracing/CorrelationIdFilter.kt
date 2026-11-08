package ingsis.snippet.runner.tracing

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {
    companion object {
        const val CORRELATION_ID_KEY = "correlation-id"
        const val CORRELATION_ID_HEADER = "X-Correlation-Id"
        const val REQUEST_ID_HEADER = "X-Request-Id"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId =
            request.getHeader(CORRELATION_ID_HEADER)?.ifBlank { null }
                ?: request.getHeader(REQUEST_ID_HEADER)?.ifBlank { null }
                ?: UUID.randomUUID().toString()

        MDC.put(CORRELATION_ID_KEY, correlationId)
        response.setHeader(CORRELATION_ID_HEADER, correlationId)
        response.setHeader(REQUEST_ID_HEADER, correlationId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(CORRELATION_ID_KEY)
        }
    }
}
