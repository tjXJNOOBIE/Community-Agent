# Discord Manager (Community Agent)

Discord Manager is a Strands-powered private control plane for operating one Discord community from ChatGPT, Claude, Codex-style workers, a local web UI, or the CLI.

The Discord bot is intentionally **not** a public AI chatbot. Ordinary Discord messages are observations, never operator instructions. Trusted Discord prompting is disabled by default and can only be enabled from machine/operator configuration. One running configuration observes and operates exactly one configured Discord guild. Gateway events from other guilds are ignored, and channel/thread reads or mutations are verified against the configured guild before Discord receives the operation.

## What it does

- Inspect guild/channel state and recent Discord activity.
- Send/edit/delete messages, create/archive threads, manage Discord scheduled events, roles, channels, pins, timeouts, kicks and bans.
- Detect repeated questions, event interest, unanswered support questions, and conflicting event time information.
- Coordinate events by creating a native Discord Scheduled Event, posting the announcement, and opening a registration/discussion thread.
- Route work through `OBSERVE`, `PROPOSE`, or `OPERATE` autonomy policy.
- Produce signed, expiring action proposals. Proposal approval exists only on the operator surface; the internal agent tool surface cannot approve its own work.
- Run machine-configured Codex/Claude-style subscription CLI workers through a bounded subprocess capability.
- Expose private MCP endpoints and a compact local web control surface, with explicit HTTPS reverse-proxy allowlists for remote use.
- Validate managed-action, event-plan, and machine-config JSON before it becomes an effect or durable state.
- Record pre-effect intent/claim evidence and mutation outcomes in private append-only local JSONL files.

## Install

Node.js 22+ is required.

```bash
npx @tjxjnoobie/community-agent install
```

The installer asks for the Discord server/guild ID and Discord application ID, creates `~/.community-agent/config.json` with mode `0600`, generates private operator/internal/proposal secrets, and prints a least-privilege **Guild Install** bot invitation URL. The generated permission bitfield includes the capabilities the runtime actually exposes, including reactions, polls, public threads, scheduled events, role/channel mutation, pins, and moderation; it intentionally excludes private-thread creation.

### Discord setup

1. Open the Discord Developer Portal and create an application.
2. Add/enable its Bot user.
3. Enable the **Message Content Intent** in the Discord Developer Portal. Discord Manager does not request the privileged Server Members Intent.
4. In the Discord desktop/web client, enable **User Settings -> Advanced -> Developer Mode** so guild/channel/user IDs can be copied.
5. Run the installer and use the generated bot invitation URL to add the bot to the target server.
6. Put the bot token in the environment. Do not commit it or place it in the config file:

```bash
export COMMUNITY_AGENT_DISCORD_BOT_TOKEN='...'
```

7. Verify the installation before starting the agent runtime:

```bash
community-agent doctor
```

The doctor checks bot-token authentication, application identity, guild access/membership, Message Content intent enablement, the exact install permission set, configured analysis/trusted channel references, effective channel permission overwrites, and role-hierarchy limitations. It returns a non-zero exit code when required setup is missing. Use `community-agent doctor --json` for machine-readable output.

8. Start the control plane:

```bash
community-agent serve
```

The default UI is available at `http://127.0.0.1:3210`.

The package does **not** expose a Discord slash command for prompting the agent.

The gateway requests exactly these intents:

```text
GUILDS
GUILD_MESSAGES
MESSAGE_CONTENT
GUILD_SCHEDULED_EVENTS
```

`MESSAGE_CONTENT` is privileged and must be enabled because proactive analysis needs message text. `GUILD_SCHEDULED_EVENTS` is a standard intent used for event lifecycle follow-up. `GUILD_MEMBERS` is intentionally not requested.

### Remote web / ChatGPT Web MCP exposure

The daemon itself remains loopback-bound. Do **not** bind it to `0.0.0.0`. To expose the operator MCP or dashboard remotely, terminate TLS in an authenticated HTTPS reverse proxy/tunnel on the same machine and explicitly configure the public host/origin:

```json
{
  "web": {
    "host": "127.0.0.1",
    "port": 3210,
    "allowedHosts": ["discord.example.com"],
    "allowedOrigins": ["https://discord.example.com"]
  }
}
```

Host checks are exact and remote browser origins must be exact HTTPS origins. Requests without an `Origin` remain valid for authenticated non-browser MCP clients, but every MCP/API route still requires its bearer token.

## Control surfaces

When `serve` is running:

- `POST /mcp/agent` is the private internal Strands tool surface and requires the generated internal bearer token.
- `POST /mcp/operator` is the private operator MCP surface and requires the generated operator bearer token.
- `/` is the local web control surface.
- `/api/*` is the local operator HTTP API and requires the generated operator bearer token. `GET /api/doctor` runs the same installation preflight as the CLI.

`/mcp/agent` deliberately has no proposal approval or trusted-controller configuration mutation tool.

The operator MCP surface can:
- send a trusted request to Discord Manager;
- inspect/analyze Discord;
- run the Discord installation doctor;
- request deterministic actions;
- approve signed proposals;
- read policy;
- read/update trusted Discord interaction, analysis, policy, worker, and reverse-proxy web configuration.

Control surfaces derive their own audit actor identity; callers cannot spoof `requestedBy` or `approvedBy` labels. Configuration updates are fully validated, atomically persisted with private permissions, audited without exposing control secrets, and require a process restart before the new runtime policy/authority is active.

## Discord interaction authority

Default configuration:

```json
{
  "trustedDiscordInteraction": {
    "enabled": false,
    "userIds": [],
    "roleIds": [],
    "channelIds": [],
    "requireMention": true
  }
}
```

A Discord message becomes a trusted operator request only when all configured boundary checks pass. Discord itself cannot modify this configuration.

Untrusted messages may still cause deterministic analysis to run. The agent receives resulting signals explicitly labeled as untrusted observation data.

## Autonomy

Every managed mutation resolves through one policy:

- `OBSERVE`: no mutation and no proposal.
- `PROPOSE`: create a signed, expiring proposal; no mutation until an external operator approves it.
- `OPERATE`: execute immediately.

Sensitive operations such as moderation, channel/role mutation, and engineering workers default to `PROPOSE` when they do not have an explicit override.

Proposal tokens are self-contained and HMAC-signed. Approval is claimed durably **before** the effect. Concurrent approval attempts therefore execute at most once, and a failed/crashed approval is intentionally one-shot rather than replayable. A fresh proposal is required after a failed claimed execution.

## Strands architecture

```text
ChatGPT / Claude / Web / CLI
            |
      operator MCP/API
            |
      Discord Manager
            |
  root Strands coordinator
     /   /   |   \   \
community moderation support events content server-ops
            |
       internal MCP
            |
 deterministic handlers/policy
       /           \
Discord REST     subscription workers
```

This repository has no direct `@strands-agents/sdk` dependency. Shared Strands runtime/MCP lifecycle behavior remains in `@tjxjnoobie/custom-strands-bridge`.

## Subscription workers

Machine configuration includes disabled-by-policy worker definitions for `codex` and `claude`. They are subprocess boundaries, not Discord commands.

Worker execution:
- uses `shell: false`;
- passes prompts on stdin;
- canonicalizes working directories with `realpath` so symlinks cannot escape machine-configured allowlists;
- passes only a small environment allowlist;
- caps combined stdout/stderr;
- escalates timeouts/output overflow from `SIGTERM` to `SIGKILL`;
- reports typed termination reason (`COMPLETED`, `TIMED_OUT`, or `OUTPUT_LIMIT`);
- is `PROPOSE` by default.

Adjust executable arguments in machine/operator configuration for the installed CLI version. Discord users cannot change worker commands or trusted execution scope.

## Development

```bash
npm install
npm run check
npm pack --dry-run
```

Architecture checks enforce the core invariants:
- no direct Strands SDK dependency;
- agent reasoning does not import `discord.js`;
- internal agent MCP cannot approve proposals;
- Discord ingress cannot mutate trusted-controller config;
- control surfaces derive audit actors rather than accepting caller-supplied labels;
- production consumers do not own mutable `Map`/`Set` fields.

See [`docs/community-agent/COMMUNITY_AGENT_FINAL_DRAFT.md`](docs/community-agent/COMMUNITY_AGENT_FINAL_DRAFT.md) and [`docs/community-agent/COMMUNITY_AGENT_PROGRESSION.md`](docs/community-agent/COMMUNITY_AGENT_PROGRESSION.md).

## Current validation boundary

The deterministic unit/E2E/architecture suite can run with Discord, Strands, MCP and worker processes substituted as true external boundaries.

Promotion still requires physical validation with the installed package set, a real Strands model provider, a real Discord development guild/bot, the HTTP MCP transport, and a clean consumer `npx` run. Those gates must not be reported as passed until they actually run.
