# Community Agent / Discord Manager demo runbook

## Status

There is no executable demo on the current `main` branch. This runbook records the required acceptance path without claiming that it has run.

## Required physical path

1. Recover the complete Discord Manager source onto `working/discord-manager-e2e`.
2. Install the package in a clean consumer and run the doctor/setup flow.
3. Configure a disposable Discord test guild and least-privilege bot token outside Git.
4. Start the real bot and authenticated operator MCP.
5. Post a safe test-guild message as an observation.
6. Produce a proposal, approve it from the operator surface, execute exactly one mutation, read the fresh Discord state, and inspect the audit result.
7. Replay the proposal and verify rejection.
8. Stop and restart both bot and operator service, then repeat MCP initialize/discovery.

## Evidence boundary

Until those steps run with the complete source, all Discord, Strands, MCP, provider, mutation, and hosted-service claims remain unverified.
