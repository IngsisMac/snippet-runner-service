package ingsis.snippet.runner

import ingsis.snippet.runner.execution.ExecutionProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ExecutionProperties::class)
class SnippetRunnerApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<SnippetRunnerApplication>(*args)
}
