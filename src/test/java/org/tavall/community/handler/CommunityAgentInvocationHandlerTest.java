package org.tavall.community.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.ai.agent.AIAgentExecutionRequest;
import org.tavall.ai.agent.AIAgentExecutionResult;
import org.tavall.ai.agent.AIAgentExecutionStatus;
import org.tavall.ai.agent.AIAgentProvider;
import org.tavall.ai.agent.AIAgentRuntime;
import org.tavall.ai.core.catalog.AIFunctionCatalog;
import org.tavall.ai.core.catalog.AIFunctionCatalogView;
import org.tavall.community.config.CommunityAgentConfiguration;
import org.tavall.community.config.CommunityAutonomyMode;
import org.tavall.community.proposal.CommunityProposalJournal;
import org.tavall.community.runtime.CommunityDependencies;
import org.tavall.dependency.maps.DependencyMap;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityAgentInvocationHandlerTest {
    @TempDir
    Path tempDirectory;

    @AfterEach
    void cleanupDependencies() {
        DependencyMap.getDependencyMap().removeDependency(CommunityDependencies.class);
    }

    @Test
    void strandsRoleCannotSeeOperatorApprovalCapabilities() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AIFunctionCatalog catalog = new AIFunctionCatalog(objectMapper);
        AtomicReference<Set<String>> visibleFunctions = new AtomicReference<>();
        AIAgentProvider strands = new AIAgentProvider() {
            @Override
            public String providerId() {
                return "strands";
            }

            @Override
            public AIAgentExecutionResult execute(AIAgentExecutionRequest request) {
                visibleFunctions.set(request.functionView().getFunctionDefinitions().keySet());
                return new AIAgentExecutionResult(
                        AIAgentExecutionStatus.COMPLETED,
                        objectMapper.createObjectNode().put("text", "done"),
                        0,
                        0,
                        null
                );
            }
        };
        AIAgentRuntime agentRuntime = new AIAgentRuntime(
                catalog,
                (root, definition, job) -> new AIFunctionCatalogView(root, ignored -> true),
                List.of(strands)
        );
        CommunityProposalJournal journal = new CommunityProposalJournal(
                objectMapper,
                tempDirectory.resolve("proposals.jsonl"),
                "secret",
                Duration.ofMinutes(15),
                Clock.systemUTC()
        );
        DependencyMap.getDependencyMap().registerInstance(
                CommunityDependencies.class,
                new CommunityDependencies(
                        configuration(tempDirectory),
                        journal,
                        Optional.empty(),
                        agentRuntime,
                        objectMapper
                )
        );
        catalog.registerInstances(List.of(
                new CommunityObservationHandler(),
                new CommunityProposalHandler(),
                new CommunityOperatorHandler(),
                new CommunityAgentInvocationHandler()
        ));

        AIAgentExecutionResult result = new CommunityAgentInvocationHandler().invoke("inspect safely");

        assertThat(result.status()).isEqualTo(AIAgentExecutionStatus.COMPLETED);
        assertThat(visibleFunctions.get()).containsExactlyInAnyOrder(
                "community_observe",
                "community_propose"
        );
        assertThat(visibleFunctions.get()).doesNotContain(
                "operator_approve",
                "operator_proposals",
                "community_invoke"
        );
    }

    private static CommunityAgentConfiguration configuration(Path dataDirectory) {
        return new CommunityAgentConfiguration(
                "guild",
                "application",
                "COMMUNITY_AGENT_DISCORD_BOT_TOKEN",
                "",
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
