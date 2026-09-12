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
import java.time.Instant;
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

    @Test
    void dispatchesReversibleMessageCommunityAndModerationActionsAfterApproval() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CommunityProposalJournal journal = new CommunityProposalJournal(
                objectMapper,
                tempDirectory.resolve("action-families.jsonl"),
                "secret",
                Duration.ofMinutes(15),
                Clock.systemUTC()
        );
        ActionRecordingGateway gateway = new ActionRecordingGateway(objectMapper);
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

        CommunityProposalHandler proposalHandler = new CommunityProposalHandler();
        CommunityOperatorHandler operatorHandler = new CommunityOperatorHandler();
        CommunityProposal edit = proposalHandler.propose(
                "edit_message",
                Map.of("channelId", "123", "messageId", "456", "content", "updated")
        );
        CommunityProposal thread = proposalHandler.propose(
                "create_thread",
                Map.of("channelId", "123", "messageId", "456", "name", "FAQ follow-up")
        );
        CommunityProposal reaction = proposalHandler.propose(
                "add_reaction",
                Map.of("channelId", "123", "messageId", "456", "emoji", "white_check_mark")
        );
        CommunityProposal timeout = proposalHandler.propose(
                "timeout_member",
                Map.of("userId", "789", "durationSeconds", 30)
        );
        CommunityProposal clear = proposalHandler.propose(
                "clear_timeout",
                Map.of("userId", "789")
        );

        assertThat(gateway.editCount).hasValue(0);
        operatorHandler.approve(edit.id());
        operatorHandler.approve(thread.id());
        operatorHandler.approve(reaction.id());
        operatorHandler.approve(timeout.id());
        operatorHandler.approve(clear.id());

        assertThat(gateway.editCount).hasValue(1);
        assertThat(gateway.threadCount).hasValue(1);
        assertThat(gateway.reactionCount).hasValue(1);
        assertThat(gateway.timeoutCount).hasValue(2);
        assertThat(gateway.lastTimeout).isNull();
    }

    private static final class ActionRecordingGateway implements DiscordGateway {
        private final ObjectMapper objectMapper;
        private final AtomicInteger editCount = new AtomicInteger();
        private final AtomicInteger threadCount = new AtomicInteger();
        private final AtomicInteger reactionCount = new AtomicInteger();
        private final AtomicInteger timeoutCount = new AtomicInteger();
        private Instant lastTimeout;

        private ActionRecordingGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

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
            return objectMapper.createObjectNode();
        }

        @Override
        public JsonNode editMessage(String channelId, String messageId, String content) {
            editCount.incrementAndGet();
            return objectMapper.createObjectNode();
        }

        @Override
        public JsonNode addReaction(String channelId, String messageId, String emoji) {
            reactionCount.incrementAndGet();
            return objectMapper.createObjectNode();
        }

        @Override
        public JsonNode createMessageThread(String channelId, String messageId, String name) {
            threadCount.incrementAndGet();
            return objectMapper.createObjectNode();
        }

        @Override
        public JsonNode timeoutMember(String userId, Instant until) {
            timeoutCount.incrementAndGet();
            lastTimeout = until;
            return objectMapper.createObjectNode();
        }
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
