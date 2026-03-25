---
name: java-method-mover
description: Move or extract Java methods into the correct class and package while preserving behavior. Use when a refactor requires relocating one or more Java methods between classes, splitting a god class, extracting services/helpers, fixing bad method placement, or handling requests like “挪 Java 方法”, “把方法移到新类”, “抽方法到 service/helper”, or “迁移后清理无用 import/字段/常量”. Bring every required helper, field, constant, import, annotation, and wiring to the target class, then remove dead imports, fields, and constants from the source class in the same change.
---

# Java Method Mover

Move Java methods by responsibility and state ownership, not by caller convenience. Make the target class compile with everything the moved code needs, and leave the source class free of dead imports, helpers, fields, and constants in the same patch.

## Non-Negotiables

- Put the method in the class that owns the data it reads or writes, or the capability it implements.
- Never paste the method at the bottom of a file blindly. Insert it near related members and follow the target file's existing ordering.
- Move the smallest cohesive unit that still compiles: method, exclusive helper chain, exclusive constants, and any state that truly changes owners.
- Do not duplicate mutable state across source and target classes.
- Keep visibility as narrow as possible. Do not make members public just to finish the move.
- Update call sites, constructor wiring, and dependency injection in the same change.
- Remove unused imports, fields, constants, helpers, and injections from the source class immediately.
- Finish with compile-ready code, not a partially relocated method.

## Choose the Target Class First

1. Put the method where the primary mutated state lives.
2. Put orchestration logic where the relevant collaborators are already owned.
3. Keep pure logic with its domain owner unless the codebase already has an established utility pattern.
4. Do not move a method just because another class calls it more often.
5. Prefer the target that can own the needed fields and constants without creating back-references to the source class.
6. If moving the method would force the target class to reach back into the source class for most of its data, the target is probably wrong.

## Build the Dependency Set Before Editing

For every method being moved, enumerate:

- fields read or written
- constants referenced
- private and package-private helper methods it calls
- annotations, JavaDoc, and `throws` clauses
- imports and static imports
- generic, nested, and fully qualified type references
- `this`, `super`, method references, and lambda captures
- constructor parameters, injected collaborators, and factory wiring
- framework behavior coupled to location, such as Spring transactional, caching, validation, or security annotations

Handle each dependency deliberately:

- Move a private helper if only the moved method chain uses it.
- Keep a shared helper with its real owner or extract a shared helper instead of duplicating logic.
- Move a private constant if the moved code becomes its only consumer.
- Keep a shared or broader-scope constant in its stable owner and update references.
- Move a field only if the target class should now own that state and its initialization.
- If the state still belongs to the source class, do not copy it. Pass it in, inject a collaborator, or keep the method where the state belongs.
- Keep local variables local unless they must become owned state in the target class.

## Perform the Move

1. Read the full source class and target class before editing. Do not move methods from snippets alone.
2. Move the full method signature, annotations, JavaDoc, and `throws` clause together.
3. Move the minimal cohesive set: the method plus every exclusive helper, constant, field, and import it requires.
4. Update constructor parameters, Lombok-generated constructor expectations, dependency injection annotations, factories, or builders if ownership changes.
5. Update call sites to use the new owner. Do not leave the source class as a pass-through wrapper unless the user explicitly asks for that.
6. If the move breaks package-private access, either choose a target that preserves legal access or redesign the dependency. Do not widen visibility without a reason.
7. Preserve behavior-sensitive annotations and confirm the target class can legally and correctly host them.

## Place the Method in the Right Spot

Follow the target class's existing member order if it is clear. If the file has no obvious convention, use this fallback order:

1. constants
2. static fields
3. instance fields
4. constructors
5. overridden methods and public API methods
6. package-private and protected methods
7. private helpers

Keep the moved method adjacent to methods of the same responsibility when possible. Place moved helpers next to the moved method or in the target file's private-helper section, not in random empty space.

## Recompute Imports and Types

- Add every import the moved code now needs in the target file, including annotations, collections, optionals, streams, nested types, and static imports.
- Remove imports from the source file when they are no longer referenced anywhere in that file.
- Re-check name collisions, same-package assumptions, and fully qualified fallback types after the move.
- Preserve or repair static imports when constants or utility methods change owners.
- Confirm the target class still compiles with its package declaration, visibility rules, and framework annotations.

## Clean the Source Class in the Same Patch

After removing the moved method, delete from the source class anything that no longer has a remaining use:

- imports
- private helpers
- fields
- constants
- constructor parameters or injected dependencies
- comments and TODOs that describe the old ownership

If a field, constant, or helper is still referenced by remaining source-class logic, keep it there.

## Final Audit

Do not stop until all of the following are true:

- The target class contains the moved method and every dependency it needs.
- The moved method sits in a sensible location inside the target class.
- The source class no longer contains dead imports, dead helpers, dead fields, or dead constants.
- No mutable state has been duplicated across source and target classes.
- Call sites, constructors, factories, and injection wiring match the new ownership.
- Imports and static imports are correct in both classes.
- Tests, compilation, or static checks have been run if the project provides them.

## Red Flags

- The target class does not own the state that the moved method mutates.
- The source class still imports types used only by the moved method.
- The target class has to call back into the source class just to make the moved method work.
- The method moved without its private helper chain.
- A constant or field was copied instead of re-owned or referenced from its true owner.
- The moved method lost annotations, JavaDoc, `throws` information, or generic imports.
- The source class still keeps now-unused injected collaborators.
