# Development Workflow

## Git branch strategy

- `main` is the integration branch and should remain in a reviewable state.
- Use short-lived branches for scoped work, for example `docs/<topic>`, `feature/<topic>`, or `fix/<topic>`.
- Keep each branch focused on one issue or one coherent change.
- Do not commit generated build output, local secrets, or unrelated changes.

## Commit convention

Use concise, imperative commit messages with a conventional type prefix, such as:

- `docs: ...` for documentation.
- `feat: ...` for an approved product capability.
- `fix: ...` for a defect correction.
- `test: ...` for tests.
- `chore: ...` for maintenance.

The subject should state the intent clearly. Product-scope changes must be reflected in the decision log or an approved planning change before implementation.

## Issue convention

Issues should have a clear title and describe one actionable concern. Include context, expected behavior or outcome, observed behavior where applicable, reproduction information for defects, and relevant environment details. Scope proposals must explain why they fit the product boundary and must not be treated as commitments until accepted.

## Pull request convention

Each pull request should:

- State the purpose and the related issue.
- Describe the files and behavior affected.
- Identify any scope or privacy implications.
- Include verification results appropriate to the change.
- Avoid unrelated refactoring or unapproved features.

Documentation-only changes should still be checked for internal consistency, links, naming, and accidental implementation instructions.

## Release flow

1. Confirm the release scope and decision records.
2. Implement and review only the approved scope.
3. Run the applicable checks and record their results.
4. Update the changelog and release documentation.
5. Review privacy, permissions, product boundaries, and user-facing claims.
6. Merge the release change to `main`, create the agreed release tag, and publish release notes.

The release process must not silently add future roadmap items to the current release.

## Codex collaboration rules

- Treat the confirmed product requirements and decision log as authoritative.
- Read the relevant project documentation before making a change.
- Make only the requested incremental change; do not begin a later task automatically.
- Do not create Android, Gradle, Kotlin, network-library, or business-code artifacts during documentation-only tasks.
- Preserve existing user changes and report any unrelated dirty-worktree content.
- Prefer explicit, auditable edits and report verification commands and results.
- Keep facts, design decisions, assumptions, and future proposals clearly separated.
- Stop after the requested task is complete and wait for the next instruction.
