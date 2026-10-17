package ingsis.snippet.redis.event

data class LintRulesChangeEvent(
    val userId: String = "",
    val rulesVersion: Int = 0,
    val rules: String = "{}",
)

data class FormatRulesChangeEvent(
    val userId: String = "",
    val rulesVersion: Int = 0,
    val rules: String = "{}",
)

data class LintRequestEvent(
    val snippetId: String = "",
    val content: String = "",
    val version: String = "1.1",
    val rulesVersion: Int = 0,
    val rules: String = "{}",
)

data class ComplianceResultEvent(
    val snippetId: String = "",
    val rulesVersion: Int = 0,
    val status: String = "PENDING",
    val findingsCount: Int = 0,
)

data class FormatRequestEvent(
    val snippetId: String = "",
    val content: String = "",
    val version: String = "1.1",
    val rulesVersion: Int = 0,
    val rules: String = "{}",
)

data class FormatResultEvent(
    val snippetId: String = "",
    val rulesVersion: Int = 0,
    val formattedContent: String = "",
    val status: String = "SUCCESS",
)
