package org.tavall.community.discord;

import com.fasterxml.jackson.databind.JsonNode;

/** External Discord REST boundary used by deterministic Community Agent capabilities. */
public interface DiscordGateway {
    JsonNode guild();

    JsonNode channels();

    JsonNode currentUser();

    JsonNode sendMessage(String channelId, String content);
}
