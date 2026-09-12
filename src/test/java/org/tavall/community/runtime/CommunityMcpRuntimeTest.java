package org.tavall.community.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.ai.agent.AIAgentRuntime;
import org.tavall.ai.core.catalog.AIFunctionCatalog;
import org.tavall.ai.core.catalog.AIFunctionCatalogView;
import org.tavall.ai.mcp.server.JacksonMcpJsonMapper;
import org.tavall.community.config.CommunityAgentConfiguration;
import org.tavall.community.config.CommunityAutonomyMode;
import org.tavall.community.handler.CommunityAgentInvocationHandler;
import org.tavall.community.handler.CommunityObservationHandler;
import org.tavall.community.handler.CommunityOperatorHandler;
import org.tavall.community.handler.CommunityProposalHandler;
import org.tavall.community.proposal.CommunityProposalJournal;
import org.tavall.dependency.maps.DependencyMap;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommunityMcpRuntimeTest {
    @TempDir
    Path tempDirectory;

    @AfterEach
    void cleanupDependencies() {
        DependencyMap.getDependencyMap().removeDependency(CommunityDependencies.class);
    }

    @Test
    void publishesDifferentToolSetsForInternalAgentAndOperator() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AIFunctionCatalog catalog = catalog(objectMapper);
        CommunityAgentConfiguration configuration = configuration(tempDirectory);

        try (CommunityMcpRuntime runtime = CommunityMcpRuntime.start(catalog, configuration, objectMapper);
             McpSyncClient agentClient = client(runtime.port(), CommunityMcpRuntime.AGENT_ENDPOINT, "agent", objectMapper);
             McpSyncClient operatorClient = client(runtime.port(), CommunityMcpRuntime.OPERATOR_ENDPOINT, "operator", objectMapper)) {
            agentClient.initialize();
            operatorClient.initialize();

            assertThat(agentClient.listTools().tools())
                    .extracting(tool -> tool.name())
                    .containsExactlyInAnyOrder("community_observe", "community_propose");
            assertThat(operatorClient.listTools().tools())
                    .extracting(tool -> tool.name())
                    .containsExactlyInAnyOrder(
                            "community_observe",
                            "community_propose",
                            "community_invoke",
                            "operator_proposals",
                            "operator_approve"
                    );
        }
    }

    @Test
    void rejectsWrongBearerTokenBeforeMcpInitialization() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AIFunctionCatalog catalog = catalog(objectMapper);

        try (CommunityMcpRuntime runtime = CommunityMcpRuntime.start(
                catalog,
                configuration(tempDirectory),
                objectMapper
        ); McpSyncClient client = client(
                runtime.port(),
                CommunityMcpRuntime.OPERATOR_ENDPOINT,
                "wrong-token",
                objectMapper
        )) {
            assertThatThrownBy(client::initialize).isInstanceOf(RuntimeException.class);
        }
    }

    private AIFunctionCatalog catalog(ObjectMapper objectMapper) {
        AIFunctionCatalog catalog = new AIFunctionCatalog(objectMapper);
        AIAgentRuntime agentRuntime = new AIAgentRuntime(
                catalog,
                (root, definition, job) -> new AIFunctionCatalogView(root, ignored -> false),
                List.of()
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
        return catalog;
    }

    private static McpSyncClient client(
            int port,
            String endpoint,
            String token,
            ObjectMapper objectMapper
    ) {
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + port)
                .endpoint(endpoint)
                .jsonMapper(jsonMapper)
                .customizeRequest(request -> request.header("Authorization", "Bearer " + token))
                .build();
        return McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(10))
                .build();
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
