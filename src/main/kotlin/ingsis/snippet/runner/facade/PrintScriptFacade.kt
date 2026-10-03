package ingsis.snippet.runner.facade

import com.printscript.common.EnvSource
import com.printscript.common.Version
import com.printscript.runner.PrintScriptRunner
import org.springframework.stereotype.Component
import java.io.StringReader
import java.io.StringWriter

@Component
class PrintScriptFacade {
    fun execute(
        content: String,
        version: String,
        inputs: List<String> = emptyList(),
        env: Map<String, String>? = null,
    ): ParseExecutionResult {
        val parsedVersion = parseVersion(version)
        val outputEmitter = BoundedOutputEmitter()
        val inputSource = QueuedInputSource(inputs)
        val envSource = env?.let { EnvSource { key -> it[key] } } ?: EnvSource.DENY

        val result =
            PrintScriptRunner.execute(
                source = StringReader(content),
                version = parsedVersion,
                output = outputEmitter,
                input = inputSource,
                env = envSource,
            )

        val errorMessages =
            result.errors.map { "${it.span.start.line}:${it.span.start.column}: ${it.message}" } +
                boundaryViolations(outputEmitter, inputSource)
        return ParseExecutionResult(
            outputs = outputEmitter.getOutputs(),
            errors = errorMessages,
            completed = errorMessages.isEmpty(),
        )
    }

    private fun boundaryViolations(
        outputEmitter: BoundedOutputEmitter,
        inputSource: QueuedInputSource,
    ): List<String> =
        buildList {
            if (outputEmitter.limitExceeded) {
                add("Output truncated: the program printed more than ${BoundedOutputEmitter.DEFAULT_MAX_LINES} lines")
            }
            if (inputSource.exhausted) {
                add("The program requested more inputs than provided (${inputSource.missingInputs().size} missing)")
            }
        }

    fun validate(
        content: String,
        version: String,
    ): ParseValidationResult {
        val parsedVersion = parseVersion(version)
        val result =
            PrintScriptRunner.validate(
                source = StringReader(content),
                version = parsedVersion,
            )
        val errorMessages = result.errors.map { "${it.span.start.line}:${it.span.start.column}: ${it.message}" }
        return ParseValidationResult(
            valid = errorMessages.isEmpty(),
            errors = errorMessages,
        )
    }

    fun format(
        content: String,
        version: String,
        rules: Map<String, Any> = emptyMap(),
    ): String {
        val parsedVersion = parseVersion(version)
        val writer = StringWriter()
        PrintScriptRunner.format(
            source = StringReader(content),
            version = parsedVersion,
            config = rules,
            writer = writer,
        )
        return writer.toString()
    }

    fun lint(
        content: String,
        version: String,
        rules: Map<String, Any> = emptyMap(),
    ): List<ParseLintFinding> {
        val parsedVersion = parseVersion(version)
        val result =
            PrintScriptRunner.analyze(
                source = StringReader(content),
                version = parsedVersion,
                config = rules,
            )
        return result.errors.map {
            ParseLintFinding(
                message = it.message,
                line = it.span.start.line,
                column = it.span.start.column,
            )
        }
    }

    fun runTest(
        content: String,
        version: String,
        inputs: List<String> = emptyList(),
        expectedOutputs: List<String> = emptyList(),
        env: Map<String, String>? = null,
    ): TestCaseExecutionResult {
        val execResult = execute(content, version, inputs, env)
        val passed = execResult.completed && execResult.outputs == expectedOutputs
        return TestCaseExecutionResult(
            passed = passed,
            actualOutputs = execResult.outputs,
            expectedOutputs = expectedOutputs,
            errors = execResult.errors,
        )
    }

    private fun parseVersion(version: String): Version = Version.from(version).getOrElse { Version.V1_1 }
}
