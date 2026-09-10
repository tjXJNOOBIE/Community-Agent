# Discord Manager (Community Agent)

Discord Manager is a Strands-powered private control plane for operating one Discord community from an authenticated MCP client or the CLI.

The Discord bot is intentionally **not** a public AI chatbot. Ordinary Discord messages are observations, never operator instructions. Trusted Discord prompting is disabled by default and can only be enabled from machine/operator configuration. One running configuration observes and operates exactly one configured Discord guild. Gateway events from other guilds are ignored, and channel/thread reads or mutations are verified against the configured guild before Discord receives the operation.

## What is runnable in this release

- Read the configured Discord guild and its channels through the typed Discord REST boundary.
- Create signed, expiring `PROPOSE` records without giving the internal agent an approval tool.
- Approve one supported typed action (`send_message`) from the authenticated operator MCP surface.
- Record proposal transitions in a private append-only JSONL file and claim before the external effect.
- Start a real Strands runtime through the shared bridge for a one-shot request; the runtime discovers the private internal MCP surface.
- Run an installation wizard, a token-safe doctor, and a loopback-only HTTP server.

The repository also contains the reviewed Discord Manager design for the remaining gateway, moderation, events, support, and worker capabilities. Those capabilities are not represented as physically passed in this release until a real Discord bot and development guild are configured.

## Install

Node.js 22+ is required.

```bash
mkdir community-manager-consumer && cd community-manager-consumer
npm init -y
npm install /path/to/tjxjnoobie-community-agent-0.1.0.tgz
npx --no-install community-agent install
```

For a source checkout, run `npm install`, `npm run build`, and use `node dist/cli/main.js` instead. The scoped npm name is retained in package metadata, but the supplied registry identity does not own the `@tjxjnoobie` scope, so this release is currently consumed from its generated tarball or a GitHub checkout rather than an npm registry install.

The installer asks for the Discord server/guild ID and Discord application ID, creates `~/.community-agent/config.json` with mode `0600`, generates private operator/internal/proposal secrets, and prints a **Guild Install** bot invitation URL. The current runnable mutation surface is deliberately limited to sending a message; do not grant Administrator permission.

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

8. Start the private control plane:

```bash
community-agent serve
```

The default UI is available at `http://127.0.0.1:3210`.

The current release does not start a Discord Gateway listener or expose a Discord slash command. REST observation and the typed message mutation are available once a bot token is configured. Gateway observation, proactive analysis, and the wider capability set remain explicitly unaccepted until a controlled Discord test guild is available.

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
- Both endpoints negotiate MCP protocol version `2025-11-25`; use an authenticated MCP client rather than treating the HTTP endpoint as anonymous.
- `/` is the local web control surface.
- `/api/*` is the local operator HTTP API and requires the generated operator bearer token. `GET /api/doctor` runs the same installation preflight as the CLI.

`/mcp/agent` deliberately has no proposal approval or trusted-controller configuration mutation tool.

The operator MCP surface can inspect the guild, create a proposal, list proposals, and approve the supported typed message action. The internal surface can inspect and create proposals but cannot approve them. Configuration and trust policy remain machine-owned.

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

The current supported action is `send_message`, and it is reached through a signed proposal. The broader sensitive-operation policy in the design document applies when those typed capabilities are implemented and physically accepted.

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

This repository has no direct `@strands-agents/sdk` dependency. Shared Strands runtime/MCP lifecycle behavior comes from `@tjxjnoobie/strands-bridge`.

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

## Validation boundary

The checked-in deterministic suite, packed consumer install, HTTP MCP initialize/discovery, proposal lifecycle, and no-token health path are runnable. Physical Discord mutation and model-backed Strands invocation require the operator to provide a real bot token, development guild, and a locally installed subscription CLI or other authorized model surface; those are not claimed as passed by this repository without that evidence.

See [`DEMO_RUNBOOK.md`](DEMO_RUNBOOK.md) for the exact local acceptance path and its honest credential boundary. See [`HACKATHON_SUBMISSION.md`](HACKATHON_SUBMISSION.md) for the hackathon description and pre-existing-component disclosure.
