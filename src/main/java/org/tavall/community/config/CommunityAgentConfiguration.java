package org.tavall.community.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable machine-owned Discord Manager configuration. */
public record CommunityAgentConfiguration(
        String guildId,
        String applicationId,
        String discordBotTokenEnvironmentVariable,
        String discordBotToken,
        String modelId,
        Path dataDirectory,
        Control control,
        Autonomy autonomy,
        Web web,
        TrustedDiscordInteraction trustedDiscordInteraction,
        Duration proposalTtl
) {
    public CommunityAgentConfiguration {
        guildId = requireText(guildId, "guildId");
        applicationId = requireText(applicationId, "applicationId");
        discordBotTokenEnvironmentVariable = requireText(
                discordBotTokenEnvironmentVariable,
                "discordBotTokenEnvironmentVariable"
        );
        discordBotToken = discordBotToken == null ? "" : discordBotToken.trim();
        modelId = modelId == null ? "" : modelId.trim();
        dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        control = Objects.requireNonNull(control, "control");
        autonomy = Objects.requireNonNull(autonomy, "autonomy");
        web = Objects.requireNonNull(web, "web");
        trustedDiscordInteraction = Objects.requireNonNull(
                trustedDiscordInteraction,
                "trustedDiscordInteraction"
        );
        proposalTtl = Objects.requireNonNull(proposalTtl, "proposalTtl");
        if (proposalTtl.isZero() || proposalTtl.isNegative()) {
            throw new IllegalArgumentException("proposalTtl must be positive");
        }
    }

    public record Control(
            String operatorToken,
            String internalAgentToken,
            String proposalSigningSecret
    ) {
        public Control {
            operatorToken = requireText(operatorToken, "operatorToken");
            internalAgentToken = requireText(internalAgentToken, "internalAgentToken");
            proposalSigningSecret = requireText(proposalSigningSecret, "proposalSigningSecret");
        }
    }

    public record Autonomy(
            CommunityAutonomyMode defaultMode,
            Map<String, CommunityAutonomyMode> overrides
    ) {
        public Autonomy {
            defaultMode = Objects.requireNonNull(defaultMode, "defaultMode");
            overrides = Map.copyOf(overrides == null ? Map.of() : overrides);
        }
    }

    public record Web(
            String host,
            int port,
            Set<String> allowedHosts,
            Set<String> allowedOrigins
    ) {
        public Web {
            host = requireText(host, "host");
            if (port < 0 || port > 65_535) {
                throw new IllegalArgumentException("port must be between 0 and 65535");
            }
            allowedHosts = Set.copyOf(allowedHosts == null ? Set.of() : allowedHosts);
            allowedOrigins = Set.copyOf(allowedOrigins == null ? Set.of() : allowedOrigins);
        }
    }

    public record TrustedDiscordInteraction(
            boolean enabled,
            Set<String> userIds,
            Set<String> roleIds,
            Set<String> channelIds,
            boolean requireMention
    ) {
        public TrustedDiscordInteraction {
            userIds = Set.copyOf(userIds == null ? Set.of() : userIds);
            roleIds = Set.copyOf(roleIds == null ? Set.of() : roleIds);
            channelIds = Set.copyOf(channelIds == null ? Set.of() : channelIds);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
