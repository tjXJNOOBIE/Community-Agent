# Superseded TypeScript backend

This directory preserves the pre-Java Discord Manager implementation as migration/reference evidence only.

It is **not** the authoritative product runtime, build, install path, MCP server, Discord authority, policy owner, or Strands integration path.

The root Java application owns the product. Strands runs in the standalone `tjXJNOOBIE/strands-bridge` MCP service and calls Java-owned Function Catalog capabilities over MCP.

Do not restore these files to the root product architecture. Port any still-useful behavior into the Java-owned domain using current Tavall architecture and tests, then shrink this legacy tree.
