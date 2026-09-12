package org.tavall.community.handler;

import com.fasterxml.jackson.databind.JsonNode;
import org.tavall.ai.core.annotation.AIFunction;
import org.tavall.ai.core.annotation.AIParam;
import org.tavall.community.proposal.CommunityProposal;
import org.tavall.community.runtime.CommunityDependencies;
import org.tavall.dependency.DependencyAccess;

import java.util.Map;
import java.util.Set;

/** Creates signed proposals; it never approves or executes them. */
public final class CommunityProposalHandler implements DependencyAccess<CommunityDependencies> {
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            "send_message",
            "edit_message",
            "add_reaction",
            "create_thread",
            "timeout_member",
            "clear_timeout"
    );

    @AIFunction(
            name = "community_propose",
            description = "Create a signed Community Agent proposal without approving or executing it."
    )
    public CommunityProposal propose(
            @AIParam(name = "action", description = "Deterministic Discord action name") String action,
            @AIParam(name = "input", description = "Action input payload") Map<String, Object> input
    ) {
        CommunityDependencies dependencies = getInstance();
        String safeAction = requireAction(action);
        JsonNode inputNode = dependencies.objectMapper().valueToTree(input == null ? Map.of() : input);
        return dependencies.proposalJournal().create(safeAction, inputNode);
    }

    private static String requireAction(String value) {
        if (value == null || value.isBlank() || !SUPPORTED_ACTIONS.contains(value.trim())) {
            throw new IllegalArgumentException(
                    "Unsupported Discord action; supported actions are " + SUPPORTED_ACTIONS
            );
        }
        return value.trim();
    }
}
