package ingsis.snippet.runner.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * El runner nunca es alcanzado por un usuario: NGINX no lo rutea y el contenedor no publica
 * puertos. Aun asi exige un JWT valido del tenant (D8): la unica forma de llegar a las rutas
 * `/runner` es con el token M2M que `snippet-service` obtiene por `client_credentials`.
 * Asi la red interna deja de ser la unica barrera.
 *
 * CORS: hoy el navegador no emite ninguna request cross-origin, porque el gateway NGINX sirve la
 * UI y la API en la misma origin y en desarrollo el proxy de Vite replica eso con la ruta relativa
 * `/api`. Declaro igual la politica en el servicio, y no en el gateway, por dos razones: queda
 * visible y testeable en el codigo, y protege al servicio tambien cuando lo llaman directo dentro
 * de la red Docker, sin pasar por NGINX.
 *
 * Las origins permitidas vienen de `CORS_ALLOWED_ORIGINS` y nunca son `*`.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Value("\${cors.allowed-origins}") private val allowedOrigins: List<String>,
) {
    /**
     * `.cors { }` del filter chain toma este bean por tipo. El `CorsFilter` corre antes de la
     * autorizacion y responde el preflight `OPTIONS` por su cuenta, asi que el preflight no
     * necesita JWT: si lo exigiera, el navegador fallaria antes de mandar la request real.
     *
     * `allowCredentials` queda en `false` a proposito: el token viaja en el header
     * `Authorization`, no en cookies, asi que habilitarlo no aportaria nada y obligaria al modo
     * mas permisivo del protocolo.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val policy = CorsConfiguration()
        policy.allowedOrigins = allowedOrigins
        policy.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        policy.allowedHeaders =
            listOf(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                CORRELATION_ID_HEADER,
            )
        policy.exposedHeaders = listOf(CORRELATION_ID_HEADER, REQUEST_ID_HEADER)
        policy.allowCredentials = false
        policy.maxAge = PREFLIGHT_MAX_AGE_SECONDS

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", policy)
        return source
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                    ).permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }

        return http.build()
    }

    private companion object {
        const val CORRELATION_ID_HEADER = "X-Correlation-Id"
        const val REQUEST_ID_HEADER = "X-Request-Id"

        /** Media hora: el navegador cachea el preflight y no lo repite en cada request. */
        const val PREFLIGHT_MAX_AGE_SECONDS = 1800L
    }
}
