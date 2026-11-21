package ingsis.snippet.runner.config

import ingsis.snippet.runner.controller.InternalRunnerController
import ingsis.snippet.runner.facade.PrintScriptFacade
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Verifica la politica de CORS declarada en [SecurityConfig]: el preflight de una origin
 * permitida se responde sin token, y el de una origin desconocida se rechaza.
 */
@WebMvcTest(InternalRunnerController::class)
@Import(SecurityConfig::class)
class CorsConfigurationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    // El preflight no llega al controller: el mock esta solo para que el contexto del slice levante.
    @MockBean
    @Suppress("UnusedPrivateProperty")
    private lateinit var facade: PrintScriptFacade

    private lateinit var allowedOrigin: String
    private lateinit var unknownOrigin: String

    @BeforeEach
    fun setUp() {
        allowedOrigin = "http://localhost:5173"
        unknownOrigin = "https://sitio-no-autorizado.example.com"
    }

    @Test
    @DisplayName("El preflight de una origin permitida se responde sin exigir token")
    fun shouldAnswerThePreflightOfAnAllowedOriginWithoutRequiringAToken() {
        mockMvc
            .perform(
                options("/runner/execute")
                    .header(HttpHeaders.ORIGIN, allowedOrigin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION),
            ).andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin))
            .andExpect(
                header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")),
            ).andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    containsString(HttpHeaders.AUTHORIZATION),
                ),
            ).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "1800"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
    }

    @Test
    @DisplayName("El preflight de una origin desconocida se rechaza y no devuelve headers de CORS")
    fun shouldRejectThePreflightOfAnUnknownOrigin() {
        mockMvc
            .perform(
                options("/runner/execute")
                    .header(HttpHeaders.ORIGIN, unknownOrigin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"),
            ).andExpect(status().isForbidden)
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }
}
