# Discord Manager demo runbook

This runbook covers the runnable, credential-safe acceptance path in this repository. It does not claim Discord or model-provider effects when those credentials are absent.

## Local acceptance

Prerequisites: Node.js 22+, a clean checkout, and no committed secrets.

```bash
npm install
gradle --no-daemon clean check installDist
npm run package:runtime
npm pack
tmp="$(mktemp -d)"
consumer="$tmp/consumer"
mkdir -p "$consumer"
(cd "$consumer" && npm init -y && npm install /path/to/tjxjnoobie-community-agent-0.2.0.tgz)
(cd "$consumer" && HOME="$tmp" npx --no-install community-agent doctor)
(cd "$consumer" && HOME="$tmp" npx --no-install community-agent install --guild-id 123456789012345678 --application-id 123456789012345678)
(cd "$consumer" && HOME="$tmp" npx --no-install community-agent serve)
```

The last command is intentionally started in a separate terminal. Verify:

```bash
curl -fsS http://127.0.0.1:3210/healthz
```

Read the generated operator token from the temporary config only on the local machine. Send `initialize`, `tools/list`, `community_propose`, `operator_proposals`, and an approval request through `POST /mcp/operator`. The approval must fail safely with no bot token rather than pretending that Discord changed.

The internal endpoint is `POST /mcp/agent`; it exposes observation and proposal tools only. Both endpoints use bearer authentication and negotiate MCP `2025-11-25`.

## Physical Discord acceptance

Requires a disposable development guild, a bot token supplied only through `COMMUNITY_AGENT_DISCORD_BOT_TOKEN`, and the bot installed with the minimum permissions needed for the supported `send_message` action.

1. Run `community-agent install` with the real guild and application IDs.
2. Add the bot using the printed invite URL.
3. Run `community-agent doctor` and preserve its JSON output privately.
4. Start `community-agent serve`.
5. Create proposals for `send_message`, bot-authored `edit_message`, and `create_thread`/`add_reaction` with a test channel/message and harmless test data. A dedicated test-user `timeout_member` followed by `clear_timeout` is the reversible moderation path.
6. Approve it through the authenticated operator MCP surface.
7. Refresh the channel in Discord and independently verify the message ID/content.
8. List proposals and retain the completed audit/proposal record for each action family.
9. Repeat the approval request with the same proposal ID; it must be rejected as already claimed.
10. Stop and restart the service, then repeat MCP initialize/discovery.

No Discord token, model credential, browser session, or operator bearer token belongs in Git. The physical Discord and model-backed gates remain unexecuted until the required external accounts are supplied.

## Cleanup

Stop the server, remove the temporary `HOME`/data directory, and delete any test message from the disposable guild through the normal Discord UI or an explicitly approved operator action.
