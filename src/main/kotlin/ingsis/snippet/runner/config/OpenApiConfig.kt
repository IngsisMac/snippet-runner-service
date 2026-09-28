package ingsis.snippet.runner.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Snippet Runner Service API")
                    .version("0.1.0")
                    .description("Isolated PrintScript execution, linting, formatting and test evaluation"),
            )
}
