# JetBrains Agent Action Bridge Drop-In

This folder is the no-build version of the JetBrains agent bridge.

Use it when you already have a JetBrains plugin project and only want a drop-in bridge for agent tools without importing the standalone example Gradle project.

## What To Copy

Copy these Kotlin files into your existing plugin project under `src/main/kotlin/`:

- `src/main/kotlin/dev/openspec/ideaagentdropin/AgentActionIds.kt`
- `src/main/kotlin/dev/openspec/ideaagentdropin/AgentToolModels.kt`
- `src/main/kotlin/dev/openspec/ideaagentdropin/AgentActionService.kt`
- `src/main/kotlin/dev/openspec/ideaagentdropin/AgentToolBridgeService.kt`
- `src/main/kotlin/dev/openspec/ideaagentdropin/ShowAgentToolCatalogAction.kt` (optional)

If you want the manual smoke-test menu action, also merge `plugin.xml.fragment` into your existing `META-INF/plugin.xml`.

## No Separate Build Required

This folder is not a standalone Gradle project.

You do not need to import or run this folder by itself. Just copy the source files into your existing plugin project and rebuild your plugin normally.

## Agent Tools

Available tool names:

- `invoke_idea_action`
- `format_code`
- `format_imports`
- `format_code_and_imports`

Example call from your agent runtime:

```kotlin
val result = project.getService(AgentToolBridgeService::class.java)
    .invokeTool("format_code_and_imports", emptyMap())
```

## Notes

- The services use `@Service`, so no XML registration is needed for the core bridge.
- `format_code_and_imports` runs `OptimizeImports` and then `ReformatCode`.
- The current editor must be active, otherwise the bridge returns `no_active_editor`.
