package org.tavall.community.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.ai.agent.AIAgentRuntime;
import org.tavall.ai.core.catalog.AIFunctionCatalog;
import org.tavall.ai.core.catalog.AIFunctionCatalogView;
import org.tavall.community.config.CommunityAgentConfiguration;
import org.tavall.community.config.CommunityAutonomyMode;
import org.tavall.community.discord.DiscordGateway;
import org.tavall.community.proposal.CommunityProposal;
import org.tavall.community.proposal.CommunityProposalJournal;
import org.tavall.community.proposal.CommunityProposalStatus;
import org.tavall.community.runtime.CommunityDependencies;
import org.tavall.dependency.maps.DependencyMap;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityOperatorHandlerTest {
    @TempDir
    Path tempDirectory;

    @AfterEach
    void cleanupDependencies() {
        DependencyMap.getDependencyMap().removeDependency(CommunityDependencies.class);
    }

    @Test
    void discordMutationOccursOnlyAfterSignedProposalIsClaimed() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CommunityProposalJournal journal = new CommunityProposalJournal(
                objectMapper,
                tempDirectory.resolve("proposals.jsonl"),
                "secret",
                Duration.ofMinutes(15),
                Clock.systemUTC()
        );
        AtomicInteger sends = new AtomicInteger();
        DiscordGateway gateway = new DiscordGateway() {
            @Override
            public JsonNode guild() {
                return objectMapper.createObjectNode();
            }

            @Override
            public JsonNode channels() {
                return objectMapper.createArrayNode();
            }

            @Override
            public JsonNode currentUser() {
                return objectMapper.createObjectNode();
            }

            @Override
            public JsonNode sendMessage(String channelId, String content) {
                sends.incrementAndGet();
                return objectMapper.createObjectNode()
                        .put("channelId", channelId)
                        .put("content", content);
            }
        };
        AIFunctionCatalog catalog = new AIFunctionCatalog(objectMapper);
        AIAgentRuntime agentRuntime = new AIAgentRuntime(
                catalog,
                (root, definition, job) -> new AIFunctionCatalogView(root, ignored -> false),
                List.of()
        );
        DependencyMap.getDependencyMap().registerInstance(
                CommunityDependencies.class,
                new CommunityDependencies(
                        configuration(tempDirectory),
                        journal,
                        Optional.of(gateway),
                        agentRuntime,
                        objectMapper
                )
        );

        CommunityProposal proposal = new CommunityProposalHandler().propose(
                "send_message",
                Map.of("channelId", "123", "content", "hello")
        );
        assertThat(sends).hasValue(0);

        CommunityProposal completed = new CommunityOperatorHandler().approve(proposal.id());

        assertThat(sends).hasValue(1);
        assertThat(completed.status()).isEqualTo(CommunityProposalStatus.COMPLETED);
        assertThat(completed.actor()).isEqualTo("authenticated-operator");
    }

    private static CommunityAgentConfiguration configuration(Path dataDirectory) {
        return new CommunityAgentConfiguration(
                "guild",
                "application",
                "COMMUNITY_AGENT_DISCORD_BOT_TOKEN",
                "token",
                "",
                dataDirectory,
                new CommunityAgentConfiguration.Control("operator", "agent", "secret"),
                new CommunityAgentConfiguration.Autonomy(CommunityAutonomyMode.PROPOSE, Map.of()),
                new CommunityAgentConfiguration.Web("127.0.0.1", 0, Set.of("127.0.0.1"), Set.of()),
                new CommunityAgentConfiguration.TrustedDiscordInteraction(
                        false,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        true
                ),
                Duration.ofMinutes(15)
        );
    }
}
