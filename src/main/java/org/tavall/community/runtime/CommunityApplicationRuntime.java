package org.tavall.community.runtime;

import org.tavall.ai.agent.strands.StrandsAgentProvider;
import org.tavall.dependency.maps.DependencyMap;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the live Java Discord Manager generation and reverse-order cleanup. */
public final class CommunityApplicationRuntime implements AutoCloseable {
    private final CommunityMcpRuntime mcpRuntime;
    private final StrandsAgentProvider strandsProvider;
    private final AtomicBoolean closed = new AtomicBoolean();

    public CommunityApplicationRuntime(
            CommunityMcpRuntime mcpRuntime,
            StrandsAgentProvider strandsProvider
    ) {
        this.mcpRuntime = Objects.requireNonNull(mcpRuntime, "mcpRuntime");
        this.strandsProvider = Objects.requireNonNull(strandsProvider, "strandsProvider");
    }

    public int port() {
        return mcpRuntime.port();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        RuntimeException failure = null;
        try {
            mcpRuntime.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            strandsProvider.close();
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        } finally {
            DependencyMap.getDependencyMap().removeDependency(CommunityDependencies.class);
        }

        if (failure != null) {
            throw failure;
        }
    }
}
