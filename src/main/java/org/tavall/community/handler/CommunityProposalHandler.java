package org.tavall.community.handler;

import com.fasterxml.jackson.databind.JsonNode;
import org.tavall.ai.core.annotation.AIFunction;
import org.tavall.ai.core.annotation.AIParam;
import org.tavall.community.proposal.CommunityProposal;
import org.tavall.community.runtime.CommunityDependencies;
import org.tavall.dependency.DependencyAccess;

import java.util.Map;

/** Creates signed proposals; it never approves or executes them. */
public final class CommunityProposalHandler implements DependencyAccess<CommunityDependencies> {
    @AIFunction(
            name = "community_propose",
            description = "Create a signed Community Agent proposal without approving or executing it."
    )
    public CommunityProposal propose(
            @AIParam(name = "action", description = "Deterministic Discord action name") String action,
            @AIParam(name = "input", description = "Action input payload") Map<String, Object> input
    ) {
        CommunityDependencies dependencies = getInstance();
        JsonNode inputNode = dependencies.objectMapper().valueToTree(input == null ? Map.of() : input);
        return dependencies.proposalJournal().create(action, inputNode);
    }
}
