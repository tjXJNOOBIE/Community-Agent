# Current Codex E2E validation

Validated on 2026-09-11 against Community PR #9 head `2a06205e501d1a16661f041885403c503ada976b`.

- Java `clean test`: passed.
- Java `check`: the local canonical architecture plugin and product test path were validated through the same composite Function Catalog source; the standalone physical bridge test was rerun as a required root test.
- Physical bridge test: passed one non-skipped `StrandsBridgeRoundTripIntegrationTest` test with Node 22, the installed Strands bridge MCP entrypoint, an ephemeral Java Streamable HTTP catalog, and a model view containing only `community_visible`.
- Packaged `installDist`: passed and produced the Java launcher.
- The authorized model view contains `community_observe` and `community_propose`; operator proposal listing, approval, and execution remain outside that view.

This was development-only validation. No production Discord guild was accessed or mutated, so no production operation record was required. Real development-guild acceptance and provider/model calls remain external gates because no development Discord credentials were supplied.
