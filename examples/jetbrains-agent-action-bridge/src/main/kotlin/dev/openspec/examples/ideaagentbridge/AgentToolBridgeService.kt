package dev.openspec.examples.ideaagentbridge

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class AgentToolBridgeService(private val project: Project) {

    fun listTools(): List<AgentToolDefinition> {
        return listOf(
            AgentToolDefinition(
                name = AgentActionIds.INVOKE_IDEA_ACTION,
                description = "Invoke a safe IntelliJ IDEA action by actionId.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "actionId" to mapOf(
                            "type" to "string",
                            "description" to "The IntelliJ IDEA action ID to invoke.",
                            "enum" to AgentActionIds.ALLOWED_IDEA_ACTIONS.sorted(),
                        ),
                    ),
                    "required" to listOf("actionId"),
                    "additionalProperties" to false,
                ),
            ),
            AgentToolDefinition(
                name = AgentActionIds.FORMAT_CODE,
                description = "Format the current file with IntelliJ reformat code.",
                inputSchema = emptyObjectSchema(),
            ),
            AgentToolDefinition(
                name = AgentActionIds.FORMAT_IMPORTS,
                description = "Optimize imports in the current file.",
                inputSchema = emptyObjectSchema(),
            ),
            AgentToolDefinition(
                name = AgentActionIds.FORMAT_CODE_AND_IMPORTS,
                description = "Optimize imports first, then reformat the current file.",
                inputSchema = emptyObjectSchema(),
            ),
        )
    }

    fun invokeTool(name: String, arguments: Map<String, Any?>): AgentToolCallResult {
        return when (name) {
            AgentActionIds.INVOKE_IDEA_ACTION -> invokeIdeaAction(arguments)
            AgentActionIds.FORMAT_CODE -> project.service<AgentActionService>().formatCode()
            AgentActionIds.FORMAT_IMPORTS -> project.service<AgentActionService>().formatImports()
            AgentActionIds.FORMAT_CODE_AND_IMPORTS -> project.service<AgentActionService>().formatCodeAndImports()
            else -> AgentToolCallResult(false, "tool_not_found: $name")
        }
    }

    fun renderCatalog(): String {
        val tools = listTools()
        val allowedValues = AgentActionIds.ALLOWED_IDEA_ACTIONS.sorted().joinToString(separator = "\n- ", prefix = "- ")
        return buildString {
            appendLine("Available tools:")
            tools.forEach { tool ->
                appendLine("- ${tool.name}: ${tool.description}")
            }
            appendLine()
            appendLine("Allowed actionId values:")
            appendLine(allowedValues)
        }
    }

    private fun invokeIdeaAction(arguments: Map<String, Any?>): AgentToolCallResult {
        val actionId = arguments["actionId"] as? String
            ?: return AgentToolCallResult(false, "missing_argument: actionId")

        return project.service<AgentActionService>().invokeIdeaAction(actionId)
    }

    private fun emptyObjectSchema(): Map<String, Any?> {
        return mapOf(
            "type" to "object",
            "properties" to emptyMap<String, Any?>(),
            "additionalProperties" to false,
        )
    }
}
