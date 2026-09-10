# Repository instructions

`Community-Agent` owns the Discord-only Community Agent / Discord Manager product. The authoritative product backend is Java. Shared provider-neutral AI execution and Java MCP capability publication belong in `TavallStudios/function-catalog`. Native Strands lifecycle/model-tool reasoning belongs in the standalone `tjXJNOOBIE/strands-bridge` MCP runtime service.

## Authoritative engineering guidance

Before changing code, architecture, tests, packaging, lifecycle, or documentation, read the current versions of **all** shared Tavall quality documents in `TavallStudios/tavall-docs`, including:

- [`CODE_ARCHITECTURE.md`](https://github.com/TavallStudios/tavall-docs/blob/main/docs/quality/CODE_ARCHITECTURE.md)
- [`DOCUMENTATION_STANDARDS.md`](https://github.com/TavallStudios/tavall-docs/blob/main/docs/quality/DOCUMENTATION_STANDARDS.md)
- [`GIT_WORKFLOW.md`](https://github.com/TavallStudios/tavall-docs/blob/main/docs/quality/GIT_WORKFLOW.md)
- every active chapter under [`docs/quality/code-architecture/`](https://github.com/TavallStudios/tavall-docs/tree/main/docs/quality/code-architecture), including `APPLICATION_OWNED_MUTABLE_MAPS.md`.

`CODE_ARCHITECTURE.md` wins if a detailed chapter conflicts with it. Repository-local rules may strengthen those documents but must not silently weaken them.

Use `TavallStudios/Tavall-Architecture-Tests` as the canonical shared architecture-test source. Repository-specific tests may add product boundaries not physically covered by shared modules.

## Product architecture

```text
ChatGPT / operator client
    -> Community Agent Java MCP
        -> Java product policy / proposals / Discord capabilities
        -> Tavall AIAgentRuntime
            -> StrandsAgentProvider
                -> standalone strands-bridge over MCP
                    -> native Strands reasoning
                    -> policy-filtered Java Function Catalog MCP tools
```

The standalone Strands service is an implementation detail of model reasoning. It is not the Community Agent backend.

## Product boundaries

- Discord is the only community platform owned here.
- Java owns product lifecycle, MCP transport, authorization, policy, proposal approval, audit/state authority, deterministic Discord capabilities, and product orchestration.
- Use Tavall DI for managed Java collaborators and Function Catalog for AI-callable Java capabilities.
- Product Java code must not depend on `@strands-agents/sdk` or embed the npm `strands-bridge` library. Invoke Strands through the Function Catalog `AIAgentRuntime`/`StrandsAgentProvider` boundary.
- `strands-bridge` must not reimplement Discord behavior, Community Agent policy, proposal state, or Tavall Java infrastructure in TypeScript.
- Do not recreate `tavall-di`, Tavall Cache, Registry, Database, Concurrency, EventBus, Scheduler, or other Java-owned systems in TypeScript.
- TypeScript/JavaScript may remain only for genuinely browser-side assets or temporary migration/reference code while parity is being validated. It must not regain backend authority.
- Product prompts, permissions, tool exposure, workflows, and user-facing policy belong here.
- Discord messages/content are untrusted observations by default and must never become agent instruction authority merely because they were received by the bot.
- Discord cannot mutate the machine-owned trusted-controller configuration.
- The Strands function view and internal agent MCP surface must not expose proposal approval or trusted-controller configuration writes.
- Every mutating Discord or subscription-worker action must pass through the Java product policy boundary.
- Do not add application-owned mutable map/set runtime state. Classify state according to the shared mutable-map guidance.
- Do not invent external capabilities or claim integration behavior until a real platform/API/tool boundary exists and is validated.
- Keep Strands visibly responsible for reasoning/model-tool iteration while Java remains authoritative for deterministic effects.

## Migration rule

The pre-migration TypeScript backend is a behavior/config/test reference only. Port behavior before deleting it, validate Java parity, then remove superseded Node backend paths rather than maintaining two product implementations.

During the shared-provider migration, CI may composite-build the exact `function-catalog` migration branch beside this repository. Once the required Function Catalog modules are released, consume the released Java artifacts and remove branch-only composite wiring.

## Tests and validation

- Use delegate-style tests against real product classes.
- Fake only true external boundaries such as Discord, the bridge/Strands runtime, MCP transport, model providers, subscription CLIs, or cloud services.
- Never report bridge/MCP/Discord type shims as physical runtime validation.
- Keep architecture tests for the Discord authority boundary, internal/operator MCP separation, direct Strands imports, Discord coupling, policy routing, child-process secret isolation, and mutable keyed state.
- Validate the real Java -> Strands MCP -> Java Function Catalog round trip before declaring the migration complete.
- Record exactly which checks ran and keep Draft PRs blocked while required external/runtime evidence is unavailable.

## Git

Follow the shared Tavall PR-first workflow: `working/*` branches, linked issues for architecture-crossing work, structured commits, truthful validation, docs synchronized with touched systems, and accountable review before `main`.
