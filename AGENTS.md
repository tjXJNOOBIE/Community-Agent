# Repository instructions

`Community-Agent` owns the Discord-only Community Agent / Discord Manager product. Shared Strands lifecycle/MCP behavior belongs in `tjXJNOOBIE/custom-strands-bridge`; product behavior stays here.

## Authoritative engineering guidance

Before changing code, architecture, tests, packaging, lifecycle, or documentation, read the current versions of **all** shared Tavall quality documents in `TavallStudios/tavall-docs`, including:

- [`CODE_ARCHITECTURE.md`](https://github.com/TavallStudios/tavall-docs/blob/main/docs/quality/CODE_ARCHITECTURE.md)
- [`DOCUMENTATION_STANDARDS.md`](https://github.com/TavallStudios/tavall-docs/blob/main/docs/quality/DOCUMENTATION_STANDARDS.md)
- [`GIT_WORKFLOW.md`](https://github.com/TavallStudios/tavall-docs/blob/main/docs/quality/GIT_WORKFLOW.md)
- every active chapter under [`docs/quality/code-architecture/`](https://github.com/TavallStudios/tavall-docs/tree/main/docs/quality/code-architecture), including `APPLICATION_OWNED_MUTABLE_MAPS.md`.

`CODE_ARCHITECTURE.md` wins if a detailed chapter conflicts with it. Repository-local rules may strengthen those documents but must not silently weaken them.

Use `TavallStudios/Tavall-Architecture-Tests` as the canonical shared architecture-test source. TypeScript product tests may add repository-specific checks for boundaries not physically covered by the shared Java-oriented modules.

## Product boundaries

- Discord is the only community platform owned here.
- Do not depend directly on `@strands-agents/sdk`; consume Strands through `@tjxjnoobie/custom-strands-bridge`.
- Do not recreate `tavall-di`, Tavall Cache, Registry, Database, Concurrency, EventBus, Scheduler, or other Java-owned systems in TypeScript. Consume owning runtimes through typed MCP/tool boundaries when needed.
- Product prompts, permissions, tool exposure, workflows, and user-facing policy belong here.
- Discord messages/content are untrusted observations by default and must never become agent instruction authority merely because they were received by the bot.
- Discord cannot mutate the machine-owned trusted-controller configuration.
- The internal agent MCP surface must not expose proposal approval or trusted-controller configuration writes.
- Every mutating Discord or subscription-worker action must pass through the product policy boundary.
- Do not add application-owned mutable map/set runtime state. Classify state according to the shared mutable-map guidance.
- Do not invent external capabilities or claim integration behavior until a real platform/API/tool boundary exists and is validated.
- Keep Strands visibly responsible for the model/tool loop.

## Tests and validation

- Use delegate-style tests against real product classes.
- Fake only true external boundaries such as Discord, the bridge/Strands runtime, MCP transport, model providers, subscription CLIs, or cloud services.
- Never report bridge/MCP/Discord type shims as physical runtime validation.
- Keep architecture tests for the Discord authority boundary, internal/operator MCP separation, direct Strands imports, Discord coupling, policy routing, and mutable keyed state.
- Record exactly which checks ran and keep Draft PRs blocked while required external/runtime evidence is unavailable.

## Git

Follow the shared Tavall PR-first workflow: `working/*` branches, linked issues for architecture-crossing work, structured commits, truthful validation, docs synchronized with touched systems, and accountable review before `main`.
