package org.tavall.community.discord;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** External Discord REST boundary used by deterministic Community Agent capabilities. */
public interface DiscordGateway {
    JsonNode guild();

    JsonNode channels();

    JsonNode currentUser();

    default JsonNode readMessage(String channelId, String messageId) {
        throw new UnsupportedOperationException("Discord message read-back is not supported by this gateway");
    }

    default JsonNode deleteMessage(String channelId, String messageId) {
        throw new UnsupportedOperationException("Discord message cleanup is not supported by this gateway");
    }

    default JsonNode deleteThread(String threadId) {
        throw new UnsupportedOperationException("Discord thread cleanup is not supported by this gateway");
    }

    JsonNode sendMessage(String channelId, String content);

    default JsonNode editMessage(String channelId, String messageId, String content) {
        throw new UnsupportedOperationException("Discord edit_message is not supported by this gateway");
    }

    default JsonNode addReaction(String channelId, String messageId, String emoji) {
        throw new UnsupportedOperationException("Discord add_reaction is not supported by this gateway");
    }

    default JsonNode createMessageThread(String channelId, String messageId, String name) {
        throw new UnsupportedOperationException("Discord create_thread is not supported by this gateway");
    }

    default JsonNode timeoutMember(String userId, Instant until) {
        throw new UnsupportedOperationException("Discord timeout_member is not supported by this gateway");
    }
}
