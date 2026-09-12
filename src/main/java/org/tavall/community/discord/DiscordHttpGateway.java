package org.tavall.community.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Discord REST v10 adapter. Product policy remains outside this platform boundary. */
public final class DiscordHttpGateway implements DiscordGateway {
    private static final URI API_ROOT = URI.create("https://discord.com/api/v10/");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String token;
    private final String guildId;

    public DiscordHttpGateway(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String token,
            String guildId
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.token = requireText(token, "token");
        this.guildId = requireText(guildId, "guildId");
    }

    @Override
    public JsonNode guild() {
        return request("guilds/" + encode(guildId), "GET", null);
    }

    @Override
    public JsonNode channels() {
        return request("guilds/" + encode(guildId) + "/channels", "GET", null);
    }

    @Override
    public JsonNode currentUser() {
        return request("users/@me", "GET", null);
    }

    @Override
    public JsonNode sendMessage(String channelId, String content) {
        String safeChannelId = requireText(channelId, "channelId");
        String safeContent = requireText(content, "content");
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("content", safeContent));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize Discord message.", exception);
        }
        return request("channels/" + encode(safeChannelId) + "/messages", "POST", body);
    }

    @Override
    public JsonNode editMessage(String channelId, String messageId, String content) {
        String safeChannelId = requireText(channelId, "channelId");
        String safeMessageId = requireText(messageId, "messageId");
        String safeContent = requireText(content, "content");
        JsonNode current = request(
                "channels/" + encode(safeChannelId) + "/messages/" + encode(safeMessageId),
                "GET",
                null
        );
        String botId = currentUser().path("id").asText("");
        String authorId = current.path("author").path("id").asText("");
        if (botId.isBlank() || !botId.equals(authorId)) {
            throw new IllegalStateException("Discord edit_message may only change a message authored by this bot");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("content", safeContent);
        body.putObject("allowed_mentions").putArray("parse");
        return request(
                "channels/" + encode(safeChannelId) + "/messages/" + encode(safeMessageId),
                "PATCH",
                writeBody(body)
        );
    }

    @Override
    public JsonNode addReaction(String channelId, String messageId, String emoji) {
        return request(
                "channels/" + encode(requireText(channelId, "channelId"))
                        + "/messages/" + encode(requireText(messageId, "messageId"))
                        + "/reactions/" + encode(requireText(emoji, "emoji")) + "/@me",
                "PUT",
                null
        );
    }

    @Override
    public JsonNode createMessageThread(String channelId, String messageId, String name) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", requireText(name, "name"));
        body.put("auto_archive_duration", 60);
        return request(
                "channels/" + encode(requireText(channelId, "channelId"))
                        + "/messages/" + encode(requireText(messageId, "messageId")) + "/threads",
                "POST",
                writeBody(body)
        );
    }

    @Override
    public JsonNode timeoutMember(String userId, Instant until) {
        if (until != null && until.isBefore(Instant.now())) {
            throw new IllegalArgumentException("timeout expiration must not be in the past");
        }
        ObjectNode body = objectMapper.createObjectNode();
        if (until == null) {
            body.putNull("communication_disabled_until");
        } else {
            body.put("communication_disabled_until", until.toString());
        }
        return request(
                "guilds/" + encode(guildId) + "/members/" + encode(requireText(userId, "userId")),
                "PATCH",
                writeBody(body)
        );
    }

    private JsonNode request(String path, String method, String body) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(API_ROOT.resolve(path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bot " + token)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Tavall-Community-Agent/0.2");
        if (body == null) {
            requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Discord request was interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Discord request failed.", exception);
        }

        JsonNode payload = parseResponse(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String discordMessage = payload.path("message").asText("request failed");
            throw new IllegalStateException(
                    "Discord API " + response.statusCode() + ": " + discordMessage
            );
        }
        return payload;
    }

    private String writeBody(ObjectNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize Discord request.", exception);
        }
    }

    private JsonNode parseResponse(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new IllegalStateException("Discord returned invalid JSON.", exception);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
