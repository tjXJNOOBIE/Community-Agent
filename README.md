# Community Agent / Discord Manager

Community Agent is intended to be a private, Strands-based Discord community operator. Discord content is observation by default; trusted operator control, proposal approval, and mutation authority belong outside untrusted Discord messages.

## Current repository status

The public `main` branch currently contains only the foundation package. The intended Discord Manager E2E implementation is not present in the authoritative repository or Git history, and the physical Discord/Strands/MCP workflow has not been proven. The open implementation request was closed rather than overstating incomplete source as a working product.

Partial recovered content is preserved on the `working/discord-manager-partial-recovery` branch at commit `7407d61bb033eb259ca4525a8435fdb0593f5722`. That branch intentionally does not build: its internal config/application/Discord doctor/install modules and complete source tree are missing. See Issue #3 for the recovery audit.

## Intended boundary

```text
private authenticated operator MCP
        -> Strands coordinator and specialists
        -> deterministic Discord policy/capability handlers
        -> Discord API

untrusted Discord messages -> observation only
operator approval -> outside internal model-facing MCP
```

The shared Strands bridge and Discord adapters are pre-existing infrastructure/components that must be disclosed if the product is rebuilt or recovered. No Discord token or provider secret is committed here.

## What is not claimed

- no installable Discord bot;
- no `doctor` or setup flow on `main`;
- no authenticated operator MCP;
- no real Strands model invocation;
- no test-guild mutation or replay-proof audit evidence;
- no hosted service or demo endpoint.

The product is not submission-ready until the complete source artifact is recovered or supplied and the physical acceptance path is run.
