package dev.openspec.examples.ideaagentbridge

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class AgentActionService(private val project: Project) {

    fun allowedActions(): Set<String> = AgentActionIds.ALLOWED_IDEA_ACTIONS

    fun invokeIdeaAction(actionId: String): AgentToolCallResult {
        return invokeEditorActions(listOf(actionId))
    }

    fun formatCode(): AgentToolCallResult {
        return invokeEditorActions(listOf(AgentActionIds.REFORMAT_CODE_ACTION))
    }

    fun formatImports(): AgentToolCallResult {
        return invokeEditorActions(listOf(AgentActionIds.OPTIMIZE_IMPORTS_ACTION))
    }

    fun formatCodeAndImports(): AgentToolCallResult {
        return invokeEditorActions(
            listOf(
                AgentActionIds.OPTIMIZE_IMPORTS_ACTION,
                AgentActionIds.REFORMAT_CODE_ACTION,
            ),
        )
    }

    private fun invokeEditorActions(actionIds: List<String>): AgentToolCallResult {
        val disallowedAction = actionIds.firstOrNull { it !in AgentActionIds.ALLOWED_IDEA_ACTIONS }
        if (disallowedAction != null) {
            return AgentToolCallResult(
                success = false,
                message = "action_not_allowed: $disallowedAction",
                data = mapOf("allowedActions" to allowedActions().sorted()),
            )
        }

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: return AgentToolCallResult(false, "no_active_editor")
        val actionManager = ActionManager.getInstance()
        val actions = actionIds.map { id ->
            actionManager.getAction(id) ?: return AgentToolCallResult(false, "action_not_found: $id")
        }

        return try {
            ApplicationManager.getApplication().invokeAndWait {
                val dataContext = DataManager.getInstance().getDataContext(editor.contentComponent)
                actions.forEach { action ->
                    ActionUtil.invokeAction(
                        action,
                        dataContext,
                        ActionPlaces.UNKNOWN,
                        null,
                        null,
                    )
                }
            }

            AgentToolCallResult(
                success = true,
                message = "ok",
                data = mapOf(
                    "actionIds" to actionIds,
                    "actionCount" to actionIds.size,
                ),
            )
        } catch (t: Throwable) {
            AgentToolCallResult(
                success = false,
                message = "invoke_failed: ${t.message ?: t::class.java.simpleName}",
                data = mapOf("actionIds" to actionIds),
            )
        }
    }
}
