package dev.openspec.ideaagentdropin

object AgentActionIds {
    const val INVOKE_IDEA_ACTION = "invoke_idea_action"
    const val FORMAT_CODE = "format_code"
    const val FORMAT_IMPORTS = "format_imports"
    const val FORMAT_CODE_AND_IMPORTS = "format_code_and_imports"

    const val REFORMAT_CODE_ACTION = "ReformatCode"
    const val OPTIMIZE_IMPORTS_ACTION = "OptimizeImports"

    val ALLOWED_IDEA_ACTIONS: Set<String> = setOf(
        REFORMAT_CODE_ACTION,
        "RenameElement",
        "FindUsages",
        "GotoDeclaration",
        OPTIMIZE_IMPORTS_ACTION,
    )
}
