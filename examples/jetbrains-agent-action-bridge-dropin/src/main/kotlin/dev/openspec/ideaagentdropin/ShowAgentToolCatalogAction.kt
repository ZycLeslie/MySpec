package dev.openspec.ideaagentdropin

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DumbAwareAction
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

class ShowAgentToolCatalogAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val content = project.service<AgentToolBridgeService>().renderCatalog()
        Messages.showInfoMessage(project, content, "Agent Tool Catalog")
    }
}
