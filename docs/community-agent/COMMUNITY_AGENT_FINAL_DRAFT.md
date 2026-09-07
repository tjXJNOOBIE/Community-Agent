# Community Agent Final Draft

> **Status:** Working product foundation  
> **Hackathon track:** Good Neighbor  
> **Shared runtime:** `@tjxjnoobie/custom-strands-bridge`  
> **Must not define:** a second Strands framework, Tavall Java infrastructure, or unverified external tool capabilities

## About

Community Agent is a distinct Agents for Humans product built on the shared Strands bridge. This repository owns the product prompt, configuration, CLI surface, product permissions, future workflow policy, and integration selection.

## Foundation flow

```text
npx @tjxjnoobie/community-agent "request"
    -> CommunityAgentCliHandler
    -> CommunityAgentRuntimeConfigBuilder
    -> IStrandsAgentRuntimeBootstrap
    -> Strands agent loop
    -> configured model and MCP/tool surfaces
    -> native result
    -> deterministic runtime close
```

The CLI validates its request before external runtime creation and closes the bridge runtime in `finally` whether invocation succeeds or fails.

## Shared runtime dependency

The package intentionally has **no direct `@strands-agents/sdk` dependency**. It currently pins `custom-strands-bridge` to exact Git commit `677f141a73fcc1bed23edf02c8fdfbd116fd034d` so the four agents can develop before bridge publication without losing one-command transitive installation.

When the bridge is promoted and published, replace the Git source with its released npm version. Do not leave an unowned temporary source dependency.

## Product configuration

- `COMMUNITY_AGENT_MODEL_ID`: optional explicit Strands model ID.
- `COMMUNITY_AGENT_MCP_URL`: optional product MCP/tool endpoint when a real integration exists.
- `COMMUNITY_AGENT_MCP_AUTHORIZATION`: optional authorization header value for the configured MCP endpoint.

Blank optional values are treated as absent. Secrets remain environment-owned.

## Current product rule

You are Community Agent. Reduce repetitive organizer work by coordinating real community information, schedules, volunteers, follow-ups, and connected services through available tools. Verify tool results before reporting an action as complete. Preserve consent and existing commitments, do not invent people or availability, and surface conflicts, sensitive choices, or ambiguous decisions to a human instead of silently deciding for them.

This is a system-level behavior contract, not proof that any particular external operation exists. Tool capability comes from the connected runtime catalog.

## Validation requirements

Before promotion from Draft:

- strict TypeScript typecheck;
- delegate tests for config construction, input rejection, invocation delegation, success cleanup, and failure cleanup;
- production build and package dry-run;
- install against the physical published or exact validated bridge package;
- physical `@strands-agents/sdk` validation inherited from the bridge promotion evidence;
- real model invocation using an authorized provider;
- real product MCP/tool smoke test when the product requires tools;
- package install/npx smoke test from a clean consumer directory;
- accurate README/demo evidence showing real action -> execution -> result/state change for claims made in the hackathon submission.

The current container may use the bridge contract shim for local compile/delegate validation, but neither this document nor the PR may call that physical Strands integration.
