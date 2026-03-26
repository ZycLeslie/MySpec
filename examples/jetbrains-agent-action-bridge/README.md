# JetBrains Agent Action Bridge

This example is a minimal IntelliJ Platform plugin project that exposes a tool-style API for an embedded AI agent.

## What You Get

- `AgentActionService`: invokes safe IntelliJ actions through the Action System.
- `AgentToolBridgeService`: exposes an agent-facing tool catalog and dispatcher.
- `ShowAgentToolCatalogAction`: a manual smoke-test action under `Tools`.

## Agent-Facing Tool

Tools:

```text
invoke_idea_action
format_code
format_imports
format_code_and_imports
```

Accepted arguments for `invoke_idea_action`:

```json
{
  "actionId": "ReformatCode"
}
```

Allowed action IDs by default:

- `ReformatCode`
- `RenameElement`
- `FindUsages`
- `GotoDeclaration`
- `OptimizeImports`

Examples:

```json
{"name":"format_code","arguments":{}}
```

```json
{"name":"format_imports","arguments":{}}
```

```json
{"name":"format_code_and_imports","arguments":{}}
```

`format_code_and_imports` runs `OptimizeImports` first and then `ReformatCode`.

## How To Try It

1. Open this folder as a Gradle project in IntelliJ IDEA.
2. Run the `runIde` Gradle task.
3. In the sandbox IDE, use `Tools -> Show Agent Tool Catalog`.
4. From your agent bridge, call `AgentToolBridgeService.listTools()` and `invokeTool(...)`.

## Integration Point

If your JetBrains plugin already has an agent runtime, wire it to:

- `dev.openspec.examples.ideaagentbridge.AgentToolBridgeService#listTools`
- `dev.openspec.examples.ideaagentbridge.AgentToolBridgeService#invokeTool`

The bridge intentionally uses action IDs instead of simulated keyboard shortcuts, so it stays stable across custom keymaps on Windows.
