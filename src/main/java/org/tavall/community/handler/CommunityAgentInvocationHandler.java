package org.tavall.community.handler;

import org.tavall.ai.agent.AIAgentDefinition;
import org.tavall.ai.agent.AIAgentExecutionBudget;
import org.tavall.ai.agent.AIAgentExecutionResult;
import org.tavall.ai.agent.AIAgentJob;
import org.tavall.ai.core.annotation.AIFunction;
import org.tavall.ai.core.annotation.AIParam;
import org.tavall.community.agent.CommunityAgentPrompt;
import org.tavall.community.runtime.CommunityDependencies;
import org.tavall.dependency.DependencyAccess;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Invokes Strands through Tavall's provider-neutral Java AIAgentRuntime. */
public final class CommunityAgentInvocationHandler implements DependencyAccess<CommunityDependencies> {
    private static final Set<String> AGENT_FUNCTIONS = Set.of(
            "community_observe",
            "community_propose"
    );

    @AIFunction(
            name = "community_invoke",
            description = "Run Discord Manager reasoning through the Java-owned Tavall agent runtime."
    )
    public AIAgentExecutionResult invoke(
            @AIParam(name = "request", description = "Authenticated operator request") String request
    ) {
        CommunityDependencies dependencies = getInstance();
        AIAgentDefinition definition = new AIAgentDefinition(
                "discord-manager",
                "Discord-only Community Agent",
                CommunityAgentPrompt.SYSTEM_PROMPT,
                "strands",
                AGENT_FUNCTIONS
        );
        AIAgentJob job = new AIAgentJob(
                UUID.randomUUID().toString(),
                requireText(request, "request"),
                0,
                Map.of("source", "community-operator-mcp")
        );
        AIAgentExecutionBudget budget = new AIAgentExecutionBudget(
                Duration.ofMinutes(5),
                20,
                0
        );
        return dependencies.agentRuntime().execute(definition, job, budget);
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
