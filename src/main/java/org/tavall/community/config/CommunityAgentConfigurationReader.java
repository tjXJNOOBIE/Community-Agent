package org.tavall.community.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reads the existing version-1 Discord Manager JSON configuration without changing its contract. */
public final class CommunityAgentConfigurationReader {
    private final ObjectMapper objectMapper;
    private final Map<String, String> environment;

    public CommunityAgentConfigurationReader(ObjectMapper objectMapper, Map<String, String> environment) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    }

    public CommunityAgentConfiguration read(Path configurationPath) {
        Path safePath = Objects.requireNonNull(configurationPath, "configurationPath")
                .toAbsolutePath()
                .normalize();
        JsonNode root;
        try {
            root = objectMapper.readTree(safePath.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Community Agent configuration: " + safePath, exception);
        }

        if (root.path("version").asInt(-1) != 1) {
            throw new IllegalArgumentException("Only Community Agent configuration version 1 is supported.");
        }

        String tokenEnvironmentVariable = requiredText(root, "discordBotTokenEnvironmentVariable");
        String discordBotToken = environment.getOrDefault(tokenEnvironmentVariable, "").trim();
        JsonNode control = requiredObject(root, "control");
        JsonNode autonomy = requiredObject(root, "autonomy");
        JsonNode web = requiredObject(root, "web");
        JsonNode trustedDiscordInteraction = requiredObject(root, "trustedDiscordInteraction");

        return new CommunityAgentConfiguration(
                requiredText(root, "guildId"),
                requiredText(root, "applicationId"),
                tokenEnvironmentVariable,
                discordBotToken,
                root.path("modelId").asText(""),
                Path.of(requiredText(root, "dataDirectory")),
                new CommunityAgentConfiguration.Control(
                        requiredText(control, "operatorToken"),
                        requiredText(control, "internalAgentToken"),
                        requiredText(control, "proposalSigningSecret")
                ),
                new CommunityAgentConfiguration.Autonomy(
                        CommunityAutonomyMode.valueOf(requiredText(autonomy, "defaultMode")),
                        autonomyOverrides(autonomy.path("overrides"))
                ),
                new CommunityAgentConfiguration.Web(
                        requiredText(web, "host"),
                        web.path("port").asInt(-1),
                        stringSet(web.path("allowedHosts")),
                        stringSet(web.path("allowedOrigins"))
                ),
                new CommunityAgentConfiguration.TrustedDiscordInteraction(
                        trustedDiscordInteraction.path("enabled").asBoolean(false),
                        stringSet(trustedDiscordInteraction.path("userIds")),
                        stringSet(trustedDiscordInteraction.path("roleIds")),
                        stringSet(trustedDiscordInteraction.path("channelIds")),
                        trustedDiscordInteraction.path("requireMention").asBoolean(true)
                ),
                Duration.ofMinutes(15)
        );
    }

    private static Map<String, CommunityAutonomyMode> autonomyOverrides(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, CommunityAutonomyMode> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(
                entry.getKey(),
                CommunityAutonomyMode.valueOf(entry.getValue().asText())
        ));
        return Map.copyOf(result);
    }

    private static Set<String> stringSet(JsonNode node) {
        if (!node.isArray()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        node.forEach(value -> {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new IllegalArgumentException("Configuration string arrays must contain non-blank strings.");
            }
            result.add(value.asText().trim());
        });
        return Set.copyOf(result);
    }

    private static JsonNode requiredObject(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (!value.isObject()) {
            throw new IllegalArgumentException(fieldName + " must be an object");
        }
        return value;
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText("").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
