package org.tavall.community.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.ai.agent.AIAgentRuntime;
import org.tavall.community.config.CommunityAgentConfiguration;
import org.tavall.community.discord.DiscordGateway;
import org.tavall.community.proposal.CommunityProposalJournal;

import java.util.Objects;
import java.util.Optional;

/** Cohesive runtime dependency bundle exposed through Tavall DI. */
public record CommunityDependencies(
        CommunityAgentConfiguration configuration,
        CommunityProposalJournal proposalJournal,
        Optional<DiscordGateway> discordGateway,
        AIAgentRuntime agentRuntime,
        ObjectMapper objectMapper
) {
    public CommunityDependencies {
        configuration = Objects.requireNonNull(configuration, "configuration");
        proposalJournal = Objects.requireNonNull(proposalJournal, "proposalJournal");
        discordGateway = Objects.requireNonNull(discordGateway, "discordGateway");
        agentRuntime = Objects.requireNonNull(agentRuntime, "agentRuntime");
        objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }
}
