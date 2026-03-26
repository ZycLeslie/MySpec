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
        if (actionId !in AgentActionIds.ALLOWED_IDEA_ACTIONS) {
            return AgentToolCallResult(
                success = false,
                message = "action_not_allowed: $actionId",
                data = mapOf("allowedActions" to allowedActions().sorted()),
            )
        }

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: return AgentToolCallResult(false, "no_active_editor")

        val action = ActionManager.getInstance().getAction(actionId)
            ?: return AgentToolCallResult(false, "action_not_found: $actionId")

        return try {
            ApplicationManager.getApplication().invokeAndWait {
                val dataContext = DataManager.getInstance().getDataContext(editor.contentComponent)
                ActionUtil.invokeAction(
                    action,
                    dataContext,
                    ActionPlaces.UNKNOWN,
                    null,
                    null,
                )
            }

            AgentToolCallResult(
                success = true,
                message = "ok",
                data = mapOf("actionId" to actionId),
            )
        } catch (t: Throwable) {
            AgentToolCallResult(
                success = false,
                message = "invoke_failed: ${t.message ?: t::class.java.simpleName}",
                data = mapOf("actionId" to actionId),
            )
        }
    }
}
