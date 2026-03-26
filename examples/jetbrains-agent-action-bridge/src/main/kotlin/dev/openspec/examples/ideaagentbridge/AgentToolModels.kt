package dev.openspec.examples.ideaagentbridge

data class AgentToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?>,
)

data class AgentToolCallResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any?> = emptyMap(),
)
