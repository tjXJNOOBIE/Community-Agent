package org.tavall.community.proposal;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;

/** Immutable signed proposal snapshot. */
public record CommunityProposal(
        String id,
        String action,
        JsonNode input,
        Instant createdAt,
        Instant expiresAt,
        CommunityProposalStatus status,
        String signature,
        JsonNode result,
        String actor
) {
    public CommunityProposal {
        id = requireText(id, "id");
        action = requireText(action, "action");
        input = Objects.requireNonNull(input, "input").deepCopy();
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        status = Objects.requireNonNull(status, "status");
        signature = requireText(signature, "signature");
        result = result == null ? null : result.deepCopy();
        actor = actor == null ? null : actor.trim();
    }

    @Override
    public JsonNode input() {
        return input.deepCopy();
    }

    @Override
    public JsonNode result() {
        return result == null ? null : result.deepCopy();
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
