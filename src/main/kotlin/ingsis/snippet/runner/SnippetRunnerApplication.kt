package ingsis.snippet.runner

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SnippetRunnerApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<SnippetRunnerApplication>(*args)
}
