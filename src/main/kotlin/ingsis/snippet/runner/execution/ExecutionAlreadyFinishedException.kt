package ingsis.snippet.runner.execution

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.CONFLICT, reason = "Execution already finished")
class ExecutionAlreadyFinishedException(
    message: String = "Execution already finished",
) : IllegalStateException(message)
