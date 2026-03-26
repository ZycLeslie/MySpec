package dev.openspec.examples.ideaagentbridge

object AgentActionIds {
    const val INVOKE_IDEA_ACTION = "invoke_idea_action"

    val ALLOWED_IDEA_ACTIONS: Set<String> = setOf(
        "ReformatCode",
        "RenameElement",
        "FindUsages",
        "GotoDeclaration",
        "OptimizeImports",
    )
}
