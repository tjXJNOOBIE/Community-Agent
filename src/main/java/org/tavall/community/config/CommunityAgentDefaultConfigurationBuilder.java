package org.tavall.community.config;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds the same secure version-1 defaults as the original installer. */
public final class CommunityAgentDefaultConfigurationBuilder {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, String> environment;

    public CommunityAgentDefaultConfigurationBuilder(Map<String, String> environment) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    }

    public CommunityAgentConfiguration build(
            String guildId,
            String applicationId,
            Path dataDirectory
    ) {
        String modelId = environment.getOrDefault("COMMUNITY_AGENT_MODEL_ID", "").trim();
        return new CommunityAgentConfiguration(
                requireText(guildId, "guildId"),
                requireText(applicationId, "applicationId"),
                "COMMUNITY_AGENT_DISCORD_BOT_TOKEN",
                "",
                modelId,
                Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize(),
                new CommunityAgentConfiguration.Control(randomSecret(), randomSecret(), randomSecret()),
                new CommunityAgentConfiguration.Autonomy(CommunityAutonomyMode.PROPOSE, Map.of()),
                new CommunityAgentConfiguration.Web(
                        "127.0.0.1",
                        3210,
                        Set.of("127.0.0.1"),
                        Set.of()
                ),
                new CommunityAgentConfiguration.TrustedDiscordInteraction(
                        false,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        true
                ),
                Duration.ofMinutes(15)
        );
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
