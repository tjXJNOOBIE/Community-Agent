# Discord Manager (Community Agent)

Discord Manager is a Java-first private control plane for operating one Discord community with Strands reasoning behind an MCP boundary.

The product architecture is intentionally split:

```text
ChatGPT / Claude / Codex / Web / CLI
                |
         product control surface
                |
      Community Agent Java runtime
  policy / approvals / audit / Discord / state
        Tavall DI + Function Catalog
                |
          agent-runtime SPI
                |
       standalone Strands bridge
             Node / TS
                |
            Strands SDK
                |
       authorized Java MCP view
                |
      Community Agent capabilities
```

**Java owns the application, authority, deterministic capabilities, policy, state, and MCP publication. Strands owns agent/model/tool-loop execution. MCP joins the two runtimes.**

The Discord bot is deliberately not a public AI chatbot. Ordinary Discord content is observation data, never instruction authority. Trusted Discord prompting is disabled by default and may only be enabled through machine/operator-owned configuration.

## Current runnable scope

The authoritative root Java runtime can:

- load the existing version-1 `~/.community-agent/config.json` configuration;
- install a Discord guild configuration with private generated control/signing secrets;
- run Discord installation diagnostics without serializing the resolved bot token;
- read the configured Discord guild/channels through a focused Java Discord gateway;
- expose observations to the agent as `untrusted_observation` data;
- create signed, expiring proposals;
- list and approve supported proposals through the operator surface;
- execute the currently supported deterministic `send_message` action only after the approval boundary;
- expose separate authenticated `/mcp/agent` and `/mcp/operator` Streamable HTTP MCP endpoints through Tavall Function Catalog;
- invoke Strands through the standalone `tjXJNOOBIE/strands-bridge` MCP runtime;
- publish only the explicitly authorized Java Function Catalog view back to Strands;
- close MCP, Strands, and DI-owned runtime resources in reverse order.

The model-facing view is hard-limited to `community_observe` and `community_propose`. `operator_approve`, proposal listing, and product-level invocation are not published into the Strands tool view.

Broader Discord Manager behavior designed during the hackathon, including gateway-driven automation, additional moderation/events/support operations, subscription workers, and the former local TS dashboard/API, is preserved under `legacy/typescript/` as migration/reference evidence. That directory is **not** an authoritative product runtime. Useful behavior must be ported into the Java-owned architecture before it is claimed as current.

## Requirements

- Java 25
- Gradle 9.1+ for a source checkout
- Node.js 22+ only for the standalone Strands bridge process
- a built checkout/install of `tjXJNOOBIE/strands-bridge`

The Community Agent itself does not embed or import the Strands npm package.

## Build

During the migration PR, local source composition can include sibling checkouts of the unmerged shared Java dependencies:

```text
../function-catalog
../tavall-di
../tavall-logging
```

Then build the Java application:

```bash
gradle --no-daemon clean test build
gradle --no-daemon installDist
```

CI additionally validates the complete Function Catalog provider and a physical Java -> Strands -> Java MCP round trip.

## Build the standalone Strands service

From `tjXJNOOBIE/strands-bridge`:

```bash
npm ci --ignore-scripts
npm run check:real
npm run build
```

Configure Community Agent with absolute runtime paths:

```bash
export COMMUNITY_AGENT_STRANDS_NODE="$(command -v node)"
export COMMUNITY_AGENT_STRANDS_ENTRYPOINT="/absolute/path/to/strands-bridge/dist/mcp/main.js"
```

The Java provider launches the bridge with an explicit sanitized environment. Product secrets such as the Discord bot token, operator token, and proposal-signing secret are not inherited by the Strands process by default.

## Install

After `gradle installDist`, the generated application launcher is:

```bash
./build/install/community-agent/bin/community-agent
```

Create the machine configuration interactively:

```bash
./build/install/community-agent/bin/community-agent install
```

or non-interactively:

```bash
./build/install/community-agent/bin/community-agent install \
  --guild-id <discord-guild-id> \
  --application-id <discord-application-id>
```

The installer creates `~/.community-agent/config.json`, generates private operator/internal/proposal-signing secrets, and prints the Discord bot invitation URL. Existing configuration is not replaced unless `--force` is supplied.

Put the Discord bot token in the environment, never in source control:

```bash
export COMMUNITY_AGENT_DISCORD_BOT_TOKEN='...'
```

Then validate the configured Discord boundary:

```bash
./build/install/community-agent/bin/community-agent doctor
```

Machine-readable doctor output is available with `doctor --json`.

## Run

Start the Java MCP/control runtime:

```bash
./build/install/community-agent/bin/community-agent serve
```

The configured Java HTTP runtime binds to the configured host/port, which defaults to loopback. Do not expose the service directly to an untrusted network.

A one-shot operator request can be sent through the Java application entrypoint:

```bash
./build/install/community-agent/bin/community-agent "Analyze my Discord server."
```

That path starts the Java product runtime, asks the standalone Strands MCP service to reason over only the authorized Java capabilities, returns the result, and tears the generation down.

## MCP authority surfaces

When `serve` is active:

- `POST /mcp/agent` requires the generated internal-agent bearer token and publishes only `community_observe` and `community_propose`;
- `POST /mcp/operator` requires the generated operator bearer token and additionally publishes product invocation, proposal listing, and approval operations.

Approval does not enter the model-facing catalog. The separation is enforced by distinct `AIFunctionCatalogView` publication, not only by prompt instructions.

## Proposal safety

The current mutation flow is:

```text
observe / reason
      -> propose
      -> signed append-only proposal journal
      -> authenticated operator approval
      -> claim proposal
      -> verify signature + expiry
      -> execute supported deterministic action
      -> journal COMPLETED or FAILED
```

Claim occurs before the external effect so concurrent approvals cannot execute the same proposal twice. A claimed failed proposal is not replayed automatically; create a fresh proposal instead.

## Discord setup

1. Create a Discord application and Bot user in the Discord Developer Portal.
2. Enable Message Content Intent when the configured observation behavior requires it.
3. Enable Discord client Developer Mode to copy guild/channel/user IDs.
4. Run `community-agent install` and use the generated guild-install invitation URL.
5. Set `COMMUNITY_AGENT_DISCORD_BOT_TOKEN` in the process environment.
6. Run `community-agent doctor`.
7. Start `community-agent serve` only after the diagnostic boundary is acceptable.

Do not grant Administrator permission merely to make setup easier. The supported capability set should drive Discord permissions.

## Validation evidence

The Java migration is intentionally gated by real boundaries rather than type shims.

Community CI validates:

1. the exact Function Catalog migration checkout with `clean check publishToMavenLocal stageRuntime`;
2. the exact standalone Strands bridge checkout and committed npm lock;
3. the Java Community build/test suite;
4. a required process-level integration where Java launches the bridge over stdio MCP, the bridge initializes the real Strands SDK, Strands connects back over Streamable HTTP MCP to an ephemeral authorized Java Function Catalog view, and the session closes cleanly.

The standalone bridge also independently runs `npm run check:real`, which exercises the real Strands SDK MCP client against a disposable MCP server.

Model-backed reasoning, real Discord mutation, and development-guild acceptance still require their respective credentials/external systems and are not claimed merely because deterministic CI is green.

## Legacy TypeScript migration evidence

`legacy/typescript/` contains the superseded Node/TypeScript product implementation and tests. It exists only to preserve already-designed behavior while the remaining capabilities are ported.

Do not:

- restore its `package.json` as the root product build;
- embed `strands-bridge` as a product npm dependency again;
- move policy, persistence, approvals, Discord authority, or product MCP ownership back into TypeScript;
- treat a legacy TS test as evidence that the Java product implements that behavior.

Port useful behavior to Java, add production-equivalent Java tests, validate it, then shrink the legacy tree.

## Documentation

The current product design lives under `docs/community-agent/`. Progression/status documentation must distinguish accepted design from physically validated implementation.

Shared engineering policy comes from current `TavallStudios/tavall-docs`, repository `AGENTS.md`, and canonical Tavall architecture tests. Current checked-in APIs win over remembered architecture shapes.
