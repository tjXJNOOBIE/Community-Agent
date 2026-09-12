package org.tavall.community.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.tavall.ai.core.annotation.AIFunction;
import org.tavall.ai.core.annotation.AIParam;
import org.tavall.community.discord.DiscordGateway;
import org.tavall.community.proposal.CommunityProposal;
import org.tavall.community.proposal.CommunityProposalStatus;
import org.tavall.community.runtime.CommunityDependencies;
import org.tavall.dependency.DependencyAccess;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/** Operator-only proposal inspection and approval/execution capabilities. */
public final class CommunityOperatorHandler implements DependencyAccess<CommunityDependencies> {
    @AIFunction(
            name = "operator_proposals",
            description = "List current Community Agent proposal states for an authenticated operator."
    )
    public List<CommunityProposal> proposals() {
        return getInstance().proposalJournal().list();
    }

    @AIFunction(
            name = "operator_approve",
            description = "Claim and execute exactly one signed Community Agent proposal."
    )
    public CommunityProposal approve(
            @AIParam(name = "proposalId", description = "Signed proposal identifier") String proposalId
    ) {
        CommunityDependencies dependencies = getInstance();
        CommunityProposal claimed = dependencies.proposalJournal().claim(
                proposalId,
                "authenticated-operator"
        );

        try {
            JsonNode result = executeClaimed(dependencies, claimed);
            return dependencies.proposalJournal().complete(
                    claimed.id(),
                    CommunityProposalStatus.COMPLETED,
                    result
            );
        } catch (RuntimeException exception) {
            ObjectNode failure = dependencies.objectMapper().createObjectNode();
            failure.put("error", safeMessage(exception));
            return dependencies.proposalJournal().complete(
                    claimed.id(),
                    CommunityProposalStatus.FAILED,
                    failure
            );
        }
    }

    private JsonNode executeClaimed(
            CommunityDependencies dependencies,
            CommunityProposal claimed
    ) {
        Optional<DiscordGateway> gateway = dependencies.discordGateway();
        if (gateway.isEmpty()) {
            throw new IllegalStateException("Discord bot token is not configured");
        }

        JsonNode input = claimed.input();
        return switch (claimed.action()) {
            case "send_message" -> gateway.get().sendMessage(
                    requiredText(input, "channelId"),
                    requiredText(input, "content")
            );
            case "edit_message" -> gateway.get().editMessage(
                    requiredText(input, "channelId"),
                    requiredText(input, "messageId"),
                    requiredText(input, "content")
            );
            case "add_reaction" -> gateway.get().addReaction(
                    requiredText(input, "channelId"),
                    requiredText(input, "messageId"),
                    requiredText(input, "emoji")
            );
            case "create_thread" -> gateway.get().createMessageThread(
                    requiredText(input, "channelId"),
                    requiredText(input, "messageId"),
                    requiredText(input, "name")
            );
            case "timeout_member" -> gateway.get().timeoutMember(
                    requiredText(input, "userId"),
                    Instant.now().plusSeconds(durationSeconds(input))
            );
            case "clear_timeout" -> gateway.get().timeoutMember(
                    requiredText(input, "userId"),
                    null
            );
            default -> throw new IllegalArgumentException("unsupported Discord action " + claimed.action());
        };
    }

    private static long durationSeconds(JsonNode input) {
        JsonNode value = input.get("durationSeconds");
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalArgumentException("durationSeconds must be an integer between 1 and 86400");
        }
        long seconds = value.longValue();
        if (seconds < 1 || seconds > 86_400) {
            throw new IllegalArgumentException("durationSeconds must be between 1 and 86400");
        }
        return seconds;
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText("").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
