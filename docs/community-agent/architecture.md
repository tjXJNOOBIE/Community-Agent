# Discord Manager architecture

```mermaid
flowchart TD
  H[Human operator / MCP host / CLI] --> O[Authenticated operator MCP\n2025-11-25]
  O --> C[Discord Manager control plane]
  C --> S[Strands runtime\nshared strands-bridge]
  S --> I[Internal MCP\nobservation + proposal only]
  I --> P[Deterministic policy + ProposalStore]
  P -->|observe| R[Discord REST API]
  P -->|approve outside model| A[Typed send_message adapter]
  A --> R
  P --> J[Private append-only JSONL\nclaim/result evidence]
  U[Discord content] -. untrusted observation .-> R
  O -. operator bearer auth .-> C
  X[Bot token] -. environment only .-> R
```

The operator surface and internal agent surface are intentionally separate. The model cannot approve its own proposal, and Discord content does not acquire instruction authority merely because it is visible to the bot.
