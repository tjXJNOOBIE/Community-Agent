# Agents for Humans submission copy

## Project

**Discord Manager** — a private, approval-gated community operator for Discord.

## Problem

Community operators spend time turning observations into safe, reviewable actions. A bot that treats every member message as an instruction is unsafe; a chatbot that cannot verify its effects is unreliable.

## Audience and value

Volunteer moderators, community managers, and small teams need a control plane that separates untrusted community content from trusted operator authority. Discord Manager makes proposals reviewable and typed before a mutation reaches Discord.

## How it works

An authenticated operator MCP client or CLI starts the Discord Manager boundary. A real Strands runtime can reason through the shared bridge, while deterministic code owns Discord REST calls, proposal signing, expiry, claim-before-effect, and result recording. The internal MCP surface cannot approve its own proposal.

The currently runnable release demonstrates guild/channel observation and one typed `send_message` mutation. The wider gateway, moderation, events, support, and worker design is documented but is not presented as physically accepted without a real test guild and credentials.

## Strands use

Strands is the agent runtime and orchestration boundary. The product keeps the model away from raw Discord mutation: the model sees bounded MCP capabilities and deterministic policy decides whether an effect is observable, proposed, or operable.

## Installation and demo

```bash
npm install
npm run check
npm run build
npm pack
npx @tjxjnoobie/community-agent install
npx @tjxjnoobie/community-agent doctor
npx @tjxjnoobie/community-agent serve
```

See [`DEMO_RUNBOOK.md`](DEMO_RUNBOOK.md) for the authenticated MCP flow and physical Discord acceptance steps.

## Pre-existing component disclosure

Built for this hackathon: the Discord Manager product boundary, proposal policy, typed Discord adapter, installer/doctor, MCP surfaces, and product documentation in this repository.

Reused infrastructure: the shared `@tjxjnoobie/strands-bridge`, Node.js/npm, Discord's APIs, MCP TypeScript packages, and Tavall development/hosting infrastructure. Credentials and provider accounts are external and are not included.

## Links

- Repository: https://github.com/tjXJNOOBIE/Community-Agent
- License: MIT
- Video: add the public video URL after recording the physical test-guild flow.
- AWS Builder ID: account-level submission field; do not place it in source control.
