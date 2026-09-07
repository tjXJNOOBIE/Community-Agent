# Community Agent

A Strands-powered agent for community coordination and repetitive organizer work.

**Agents for Humans track:** Good Neighbor

## Install and run

```bash
npx @tjxjnoobie/community-agent "your request"
```

Node.js 22+ is required. Model-provider credentials/configuration are supplied through Strands and the environment. Set `COMMUNITY_AGENT_MODEL_ID` to select an explicit Strands model ID.

Set `COMMUNITY_AGENT_MCP_URL` when a real product tool/MCP surface is available. The foundation does not fabricate one.

Optional MCP authorization may be supplied through `COMMUNITY_AGENT_MCP_AUTHORIZATION`. Secrets are never committed.

## Architecture

```text
Community Agent
    -> @tjxjnoobie/custom-strands-bridge
        -> @strands-agents/sdk
            -> model provider
            -> typed MCP/tool surfaces
                -> owning Tavall/third-party runtimes
```

This repository owns Community Agent product behavior. Shared Strands bootstrap, MCP composition, invocation, cancellation, agent-as-tool composition, and cleanup remain in `custom-strands-bridge`. Tavall Java infrastructure remains in its owning Java runtimes and is consumed over typed boundaries instead of being recreated in TypeScript.

## Development

```bash
npm install
npm run check
```

The bridge dependency is temporarily pinned to exact Git commit `677f141a73fcc1bed23edf02c8fdfbd116fd034d` while the bridge foundation is still in Draft review. Replace that source pin with the published bridge version once the bridge promotion gates are complete.

See [`docs/community-agent/COMMUNITY_AGENT_FINAL_DRAFT.md`](docs/community-agent/COMMUNITY_AGENT_FINAL_DRAFT.md) for the current product foundation contract.
