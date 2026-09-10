package org.tavall.community.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Preserves the existing ~/.community-agent path contract for the Java backend. */
public final class CommunityAgentPathResolver {
    private final Map<String, String> environment;

    public CommunityAgentPathResolver(Map<String, String> environment) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    }

    public Path dataDirectory() {
        String configured = environment.get("COMMUNITY_AGENT_DATA_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".community-agent").toAbsolutePath().normalize();
    }

    public Path configFile() {
        return dataDirectory().resolve("config.json");
    }

    public Path proposalFile() {
        return dataDirectory().resolve("proposals.jsonl");
    }

    public Path auditFile() {
        return dataDirectory().resolve("audit.jsonl");
    }
}
