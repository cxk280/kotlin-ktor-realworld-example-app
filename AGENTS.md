# AGENTS.md

Guidance for AI agents (OpenAI Codex reviews, Claude Code fixes) working on this
repository. This is a [RealWorld](https://github.com/gothinkster/realworld) API
implemented in **Kotlin + Ktor + Exposed + Kodein (DI) + H2 (in-memory)**.

## Build & test

```bash
./gradlew clean build   # compile + test
./gradlew test          # tests only
./gradlew run           # start server on :8080
```

JDK 16+ is required (`jvmTarget = "16"`). CI runs the build/test matrix on JDK 17 and 21.

## Architecture & conventions

- **Layering is strict:** `web/controllers` → `domain/service` → `domain/repository`.
  A controller must not talk to the database directly; put business logic in the
  service and data access in the repository.
- **Routing** is wired in `web/Router.kt`. New endpoints go through the router, not
  ad-hoc handlers.
- **Auth:** protected endpoints use JWT. Follow the existing auth pattern — don't
  invent a new mechanism. Require authentication wherever the RealWorld spec does.
- **Error handling:** return the same status codes the existing controllers use —
  `401` (unauthenticated), `404` (not found), `422` (validation). Keep error bodies
  consistent with existing responses.
- **Kotlin style:** idiomatic, immutable-by-default (`val`), null-safety over
  `!!`, data classes for DTOs. Match the style of the surrounding file.

## Testing conventions

- Integration tests boot the full app via `src/test/kotlin/.../rules/AppRule.kt`.
  Use that rule rather than mocking the framework.
- Follow the style in `web/controllers/UserControllerTest.kt`.
- Every new endpoint needs: the happy path plus at least two error cases
  (e.g. unauthenticated `401`, not-found `404`, invalid input `422`).
- The H2 DB is in-memory and resets between runs — no external setup.

## Review guidelines (for Codex)

When reviewing a PR, prioritize, in order:

1. **Correctness** — does the endpoint do what the spec asks? Pagination
   (`limit`/`offset`), sorting, and edge cases (empty results, missing auth) handled?
2. **Layering** — no controller reaching past the service/repository boundary.
3. **Auth & error codes** — protected routes actually protected; `401/404/422`
   used consistently.
4. **Tests** — happy path + ≥2 error cases present and meaningful (real assertions,
   not just status-code checks).
5. **Idiomatic Kotlin** — flag `!!`, mutable state, and deviations from surrounding style.

Keep review comments specific and actionable — point at the file/line and say what
to change. Don't request unrelated refactors or scope creep beyond the PR's feature.
