---
name: pom-version-governance
description: Audit and consolidate duplicated Maven POM version definitions across multi-module projects (SDK, gateway, microservices). Use when users ask to “整理 pom”, “统一依赖版本”, “检查重复定义的 pom”, “治理 Maven 版本漂移”, or need to find duplicate/conflicting dependency, plugin, or property versions before centralizing them into parent POM/BOM.
---

# POM Version Governance

Identify repeated and conflicting Maven version definitions, then drive them into a centralized governance model (`dependencyManagement`, `pluginManagement`, and shared `properties`).

## Workflow

1. Run the scanner to collect repeated definitions:

```bash
python3 scripts/audit_pom_versions.py --root <project-root>
```

2. Focus on `Conflicting Version Definitions` first:
   - Same `groupId:artifactId` with multiple versions.
   - Same property key with different values.

3. Then handle `Repeated Identical Definitions`:
   - Same dependency/plugin version repeated in many child POMs.
   - Same property key/value copied across modules.

4. Produce a consolidation change plan:
   - Create or confirm a root parent/BOM.
   - Move shared versions to root `dependencyManagement` and `pluginManagement`.
   - Keep shared version properties in one place.
   - Remove child-level explicit versions that are already managed.

## Scanner Commands

1. Basic scan:

```bash
python3 scripts/audit_pom_versions.py --root .
```

2. Tighten candidate threshold (only show entries repeated 3+ times):

```bash
python3 scripts/audit_pom_versions.py --root . --min-occurrences 3
```

3. CI mode (non-zero exit when conflicts exist):

```bash
python3 scripts/audit_pom_versions.py --root . --fail-on-conflict
```

4. Export machine-readable output:

```bash
python3 scripts/audit_pom_versions.py --root . --format json --output pom-version-audit.json
```

## Interpretation Rules

1. Dependency conflict:
   - Same `groupId:artifactId` with different versions across modules.
   - Treat as highest priority because runtime/classpath behavior may diverge.

2. Plugin conflict:
   - Same `groupId:artifactId` plugin with different versions.
   - Treat as build stability risk across services.

3. Property conflict:
   - Same property key appears with different values.
   - Resolve by choosing one source of truth in parent/BOM.

4. Identical repetition:
   - Not an immediate bug, but indicates poor maintainability.
   - Consolidate to reduce upgrade cost and drift risk.

## Consolidation Strategy for SDK + Gateway + Microservices

1. Define one governance root:
   - Root parent POM or dedicated BOM module.
2. Centralize shared libraries:
   - Framework BOM imports and common runtime dependencies.
3. Centralize build plugins:
   - Compiler/surefire/failsafe/jacoco/checkstyle versions.
4. Keep only module-specific versions local:
   - A version should remain local only if truly module-exclusive.
5. Re-run scanner after cleanup:
   - Ensure conflicts are gone and repetitive definitions reduced.

## Guardrails

1. Do not remove a child version blindly unless the parent/BOM manages it.
2. Preserve intentionally pinned module-specific versions with a comment/rationale.
3. Prefer `dependencyManagement` over copying versions into each child dependency declaration.
4. Prioritize behavior safety: resolve conflicts before deduplicating same-version repetitions.
