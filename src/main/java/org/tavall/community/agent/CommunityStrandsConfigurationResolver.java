package org.tavall.community.agent;

import org.tavall.ai.agent.strands.StrandsAgentProviderConfiguration;
import org.tavall.community.config.CommunityAgentConfiguration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resolves the deliberately narrow environment passed from Java into the Strands process. */
public final class CommunityStrandsConfigurationResolver {
    public static final String NODE_EXECUTABLE_ENV = "COMMUNITY_AGENT_STRANDS_NODE";
    public static final String BRIDGE_ENTRYPOINT_ENV = "COMMUNITY_AGENT_STRANDS_ENTRYPOINT";

    private static final Set<String> ALLOWED_STRANDS_ENVIRONMENT = Set.of(
            "PATH",
            "HOME",
            "TMPDIR",
            "TMP",
            "TEMP",
            "NODE_EXTRA_CA_CERTS",
            "SSL_CERT_FILE",
            "AWS_REGION",
            "AWS_DEFAULT_REGION",
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "AWS_SESSION_TOKEN",
            "AWS_PROFILE",
            "AWS_SDK_LOAD_CONFIG",
            "ANTHROPIC_API_KEY",
            "OPENAI_API_KEY",
            "GOOGLE_API_KEY",
            "GEMINI_API_KEY"
    );

    private final Map<String, String> environment;

    public CommunityStrandsConfigurationResolver(Map<String, String> environment) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    }

    public StrandsAgentProviderConfiguration resolve(CommunityAgentConfiguration configuration) {
        CommunityAgentConfiguration safeConfiguration = Objects.requireNonNull(configuration, "configuration");
        Path nodeExecutable = Path.of(requiredEnvironment(NODE_EXECUTABLE_ENV));
        Path bridgeEntrypoint = Path.of(requiredEnvironment(BRIDGE_ENTRYPOINT_ENV));

        Map<String, String> strandsEnvironment = new LinkedHashMap<>();
        for (String name : ALLOWED_STRANDS_ENVIRONMENT) {
            String value = environment.get(name);
            if (value != null && !value.isBlank()) {
                strandsEnvironment.put(name, value);
            }
        }

        return StrandsAgentProviderConfiguration.node(
                nodeExecutable,
                bridgeEntrypoint,
                strandsEnvironment,
                Duration.ofMinutes(5),
                safeConfiguration.modelId()
        );
    }

    private String requiredEnvironment(String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must point to the standalone Strands runtime installation");
        }
        return value.trim();
    }
}
