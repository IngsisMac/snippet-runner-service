package ingsis.snippet.runner.controller

import ingsis.snippet.runner.controller.dto.ExecuteRequest
import ingsis.snippet.runner.controller.dto.ExecuteResponse
import ingsis.snippet.runner.controller.dto.FormatRequest
import ingsis.snippet.runner.controller.dto.FormatResponse
import ingsis.snippet.runner.controller.dto.LintFindingDto
import ingsis.snippet.runner.controller.dto.LintRequest
import ingsis.snippet.runner.controller.dto.LintResponse
import ingsis.snippet.runner.controller.dto.TestSnippetRequest
import ingsis.snippet.runner.controller.dto.TestSnippetResponse
import ingsis.snippet.runner.controller.dto.ValidateRequest
import ingsis.snippet.runner.controller.dto.ValidateResponse
import ingsis.snippet.runner.facade.PrintScriptFacade
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/runner")
class InternalRunnerController(
    private val facade: PrintScriptFacade,
) {
    @PostMapping("/execute")
    fun execute(
        @RequestBody request: ExecuteRequest,
    ): ExecuteResponse {
        val result =
            facade.execute(
                content = request.content,
                version = request.version,
                inputs = request.inputs,
                env = request.env,
            )
        return ExecuteResponse(
            outputs = result.outputs,
            errors = result.errors,
            completed = result.completed,
        )
    }

    @PostMapping("/validate")
    fun validate(
        @RequestBody request: ValidateRequest,
    ): ValidateResponse {
        val result =
            facade.validate(
                content = request.content,
                version = request.version,
            )
        return ValidateResponse(
            valid = result.valid,
            errors = result.errors,
        )
    }

    @PostMapping("/format")
    fun format(
        @RequestBody request: FormatRequest,
    ): FormatResponse {
        val formatted =
            facade.format(
                content = request.content,
                version = request.version,
                rules = request.rules,
            )
        return FormatResponse(formattedContent = formatted)
    }

    @PostMapping("/lint")
    fun lint(
        @RequestBody request: LintRequest,
    ): LintResponse {
        val findings =
            facade.lint(
                content = request.content,
                version = request.version,
                rules = request.rules,
            )
        val dtoList = findings.map { LintFindingDto(it.message, it.line, it.column) }
        return LintResponse(
            findings = dtoList,
            findingsCount = dtoList.size,
        )
    }

    @PostMapping("/test")
    fun runTest(
        @RequestBody request: TestSnippetRequest,
    ): TestSnippetResponse {
        val result =
            facade.runTest(
                content = request.content,
                version = request.version,
                inputs = request.inputs,
                expectedOutputs = request.expectedOutputs,
                env = request.env,
            )
        return TestSnippetResponse(
            passed = result.passed,
            actualOutputs = result.actualOutputs,
            expectedOutputs = result.expectedOutputs,
            errors = result.errors,
        )
    }
}
