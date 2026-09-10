package org.tavall.community.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.ai.agent.AIAgentRuntime;
import org.tavall.ai.agent.strands.StrandsAgentProvider;
import org.tavall.ai.agent.strands.StrandsAgentProviderConfiguration;
import org.tavall.ai.core.catalog.AIFunctionCatalog;
import org.tavall.ai.core.catalog.AIFunctionCatalogView;
import org.tavall.community.agent.CommunityStrandsConfigurationResolver;
import org.tavall.community.config.CommunityAgentConfiguration;
import org.tavall.community.config.CommunityAgentConfigurationReader;
import org.tavall.community.discord.DiscordGateway;
import org.tavall.community.discord.DiscordHttpGateway;
import org.tavall.community.handler.CommunityAgentInvocationHandler;
import org.tavall.community.handler.CommunityObservationHandler;
import org.tavall.community.handler.CommunityOperatorHandler;
import org.tavall.community.handler.CommunityProposalHandler;
import org.tavall.community.proposal.CommunityProposalJournal;
import org.tavall.dependency.maps.DependencyMap;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Java composition root for Discord Manager. */
public final class CommunityApplicationBootstrap {
    public static final String PROPOSAL_FILE_ENV = "COMMUNITY_AGENT_PROPOSAL_FILE";

    private static final Set<String> STRANDS_FUNCTIONS = Set.of(
            "community_observe",
            "community_propose"
    );

    private CommunityApplicationBootstrap() {
    }

    public static CommunityApplicationRuntime start(
            Path configurationPath,
            Map<String, String> environment
    ) {
        Map<String, String> safeEnvironment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CommunityAgentConfiguration configuration = new CommunityAgentConfigurationReader(
                objectMapper,
                safeEnvironment
        ).read(configurationPath);

        StrandsAgentProviderConfiguration strandsConfiguration = new CommunityStrandsConfigurationResolver(
                safeEnvironment
        ).resolve(configuration);
        StrandsAgentProvider strandsProvider = new StrandsAgentProvider(strandsConfiguration);
        AIFunctionCatalog catalog = new AIFunctionCatalog(objectMapper);
        AIAgentRuntime agentRuntime = new AIAgentRuntime(
                catalog,
                (root, definition, job) -> new AIFunctionCatalogView(
                        root,
                        function -> STRANDS_FUNCTIONS.contains(function.getName())
                ),
                List.of(strandsProvider)
        );

        Path proposalPath = proposalPath(configuration, safeEnvironment);
        CommunityProposalJournal proposalJournal = new CommunityProposalJournal(
                objectMapper,
                proposalPath,
                configuration.control().proposalSigningSecret(),
                configuration.proposalTtl(),
                Clock.systemUTC()
        );
        Optional<DiscordGateway> discordGateway = discordGateway(configuration, objectMapper);
        CommunityDependencies dependencies = new CommunityDependencies(
                configuration,
                proposalJournal,
                discordGateway,
                agentRuntime,
                objectMapper
        );
        DependencyMap.getDependencyMap().registerInstance(CommunityDependencies.class, dependencies);

        try {
            catalog.registerInstances(List.of(
                    new CommunityObservationHandler(),
                    new CommunityProposalHandler(),
                    new CommunityOperatorHandler(),
                    new CommunityAgentInvocationHandler()
            ));
            CommunityMcpRuntime mcpRuntime = CommunityMcpRuntime.start(catalog, configuration, objectMapper);
            return new CommunityApplicationRuntime(mcpRuntime, strandsProvider);
        } catch (RuntimeException exception) {
            DependencyMap.getDependencyMap().removeDependency(CommunityDependencies.class);
            try {
                strandsProvider.close();
            } catch (RuntimeException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    private static Optional<DiscordGateway> discordGateway(
            CommunityAgentConfiguration configuration,
            ObjectMapper objectMapper
    ) {
        if (configuration.discordBotToken().isBlank()) {
            return Optional.empty();
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return Optional.of(new DiscordHttpGateway(
                httpClient,
                objectMapper,
                configuration.discordBotToken(),
                configuration.guildId()
        ));
    }

    private static Path proposalPath(
            CommunityAgentConfiguration configuration,
            Map<String, String> environment
    ) {
        String override = environment.get(PROPOSAL_FILE_ENV);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return configuration.dataDirectory().resolve("proposals.jsonl").toAbsolutePath().normalize();
    }
}
