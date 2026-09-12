package org.tavall.community.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.tavall.ai.core.catalog.AIFunctionCatalog;
import org.tavall.ai.core.catalog.AIFunctionCatalogView;
import org.tavall.ai.mcp.server.AIFunctionMcpToolPublisher;
import org.tavall.ai.mcp.server.JacksonMcpJsonMapper;
import org.tavall.community.config.CommunityAgentConfiguration;
import org.tavall.community.http.CommunityHttpSecurityFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the Java Streamable HTTP MCP lifecycle for operator and internal-agent surfaces. */
public final class CommunityMcpRuntime implements AutoCloseable {
    public static final String OPERATOR_ENDPOINT = "/mcp/operator";
    public static final String AGENT_ENDPOINT = "/mcp/agent";

    private static final Set<String> AGENT_FUNCTIONS = Set.of(
            "community_observe",
            "community_propose"
    );
    private static final Set<String> OPERATOR_FUNCTIONS = Set.of(
            "community_observe",
            "community_propose",
            "community_invoke",
            "operator_proposals",
            "operator_approve"
    );

    private final Path baseDirectory;
    private final Tomcat tomcat;
    private final AIFunctionCatalogView agentView;
    private final AIFunctionCatalogView operatorView;
    private final List<McpSyncServer> servers;
    private final List<HttpServletStreamableServerTransportProvider> transports;
    private final AtomicBoolean closed = new AtomicBoolean();

    private CommunityMcpRuntime(
            Path baseDirectory,
            Tomcat tomcat,
            AIFunctionCatalogView agentView,
            AIFunctionCatalogView operatorView,
            List<McpSyncServer> servers,
            List<HttpServletStreamableServerTransportProvider> transports
    ) {
        this.baseDirectory = baseDirectory;
        this.tomcat = tomcat;
        this.agentView = agentView;
        this.operatorView = operatorView;
        this.servers = List.copyOf(servers);
        this.transports = List.copyOf(transports);
    }

    public static CommunityMcpRuntime start(
            AIFunctionCatalog catalog,
            CommunityAgentConfiguration configuration,
            ObjectMapper objectMapper
    ) {
        AIFunctionCatalog safeCatalog = Objects.requireNonNull(catalog, "catalog");
        CommunityAgentConfiguration safeConfiguration = Objects.requireNonNull(configuration, "configuration");
        ObjectMapper safeObjectMapper = Objects.requireNonNull(objectMapper, "objectMapper");

        AIFunctionCatalogView agentView = new AIFunctionCatalogView(
                safeCatalog,
                function -> AGENT_FUNCTIONS.contains(function.getName())
        );
        AIFunctionCatalogView operatorView = new AIFunctionCatalogView(
                safeCatalog,
                function -> OPERATOR_FUNCTIONS.contains(function.getName())
        );
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(safeObjectMapper);
        AIFunctionMcpToolPublisher publisher = new AIFunctionMcpToolPublisher(safeObjectMapper);

        HttpServletStreamableServerTransportProvider agentTransport = transport(jsonMapper, AGENT_ENDPOINT);
        HttpServletStreamableServerTransportProvider operatorTransport = transport(jsonMapper, OPERATOR_ENDPOINT);
        McpSyncServer agentServer = server(
                agentTransport,
                jsonMapper,
                publisher,
                agentView,
                "Discord Manager Internal Agent MCP",
                "Untrusted observations and proposal creation only. Approval is intentionally unavailable."
        );
        McpSyncServer operatorServer = server(
                operatorTransport,
                jsonMapper,
                publisher,
                operatorView,
                "Discord Manager Operator MCP",
                "Authenticated operator control surface for reasoning, proposals, and explicit approvals."
        );

        Path baseDirectory = createBaseDirectory();
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(baseDirectory.toString());
        tomcat.setPort(safeConfiguration.web().port());
        tomcat.getConnector();
        tomcat.getConnector().setProperty("address", safeConfiguration.web().host());
        Context context = tomcat.addContext("", baseDirectory.toAbsolutePath().toString());

        registerMcpServlet(context, "communityAgentMcp", AGENT_ENDPOINT, agentTransport);
        registerMcpServlet(context, "communityOperatorMcp", OPERATOR_ENDPOINT, operatorTransport);
        registerSecurityFilter(
                context,
                "communityAgentAuth",
                AGENT_ENDPOINT,
                new CommunityHttpSecurityFilter(
                        safeConfiguration.control().internalAgentToken(),
                        safeConfiguration.web().allowedHosts(),
                        safeConfiguration.web().allowedOrigins()
                )
        );
        registerSecurityFilter(
                context,
                "communityOperatorAuth",
                OPERATOR_ENDPOINT,
                new CommunityHttpSecurityFilter(
                        safeConfiguration.control().operatorToken(),
                        safeConfiguration.web().allowedHosts(),
                        safeConfiguration.web().allowedOrigins()
                )
        );
        registerStatusServlet(context, safeConfiguration.guildId());

        try {
            tomcat.start();
            return new CommunityMcpRuntime(
                    baseDirectory,
                    tomcat,
                    agentView,
                    operatorView,
                    List.of(agentServer, operatorServer),
                    List.of(agentTransport, operatorTransport)
            );
        } catch (Exception exception) {
            closeQuietly(agentServer, operatorServer, agentTransport, operatorTransport, tomcat);
            deleteQuietly(baseDirectory);
            throw new IllegalStateException("Failed to start Discord Manager Java MCP runtime.", exception);
        }
    }

    public int port() {
        return tomcat.getConnector().getLocalPort();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        agentView.revoke();
        operatorView.revoke();
        RuntimeException failure = null;

        for (int index = servers.size() - 1; index >= 0; index--) {
            try {
                servers.get(index).close();
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            }
        }
        try {
            tomcat.stop();
            tomcat.destroy();
        } catch (Exception exception) {
            failure = appendFailure(failure, new IllegalStateException("Failed to stop Discord Manager HTTP runtime.", exception));
        }
        for (int index = transports.size() - 1; index >= 0; index--) {
            try {
                transports.get(index).close();
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            }
        }
        try {
            deleteRecursively(baseDirectory);
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static HttpServletStreamableServerTransportProvider transport(
            McpJsonMapper jsonMapper,
            String endpoint
    ) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(endpoint)
                .build();
    }

    private static McpSyncServer server(
            HttpServletStreamableServerTransportProvider transport,
            McpJsonMapper jsonMapper,
            AIFunctionMcpToolPublisher publisher,
            AIFunctionCatalogView view,
            String name,
            String instructions
    ) {
        return McpServer.sync(transport)
                .serverInfo(name, "0.2.0")
                .instructions(instructions)
                .jsonMapper(jsonMapper)
                .tools(publisher.viewToolSpecifications(view))
                .build();
    }

    private static void registerMcpServlet(
            Context context,
            String name,
            String endpoint,
            HttpServlet servlet
    ) {
        Tomcat.addServlet(context, name, servlet);
        context.addServletMappingDecoded(endpoint, name);
        context.addServletMappingDecoded(endpoint + "/*", name);
    }

    private static void registerSecurityFilter(
            Context context,
            String name,
            String endpoint,
            CommunityHttpSecurityFilter filter
    ) {
        FilterDef definition = new FilterDef();
        definition.setFilterName(name);
        definition.setFilter(filter);
        definition.setFilterClass(filter.getClass().getName());
        context.addFilterDef(definition);

        FilterMap mapping = new FilterMap();
        mapping.setFilterName(name);
        mapping.addURLPattern(endpoint);
        mapping.addURLPattern(endpoint + "/*");
        context.addFilterMap(mapping);
    }

    private static void registerStatusServlet(Context context, String guildId) {
        HttpServlet statusServlet = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/json; charset=utf-8");
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.getWriter().write(
                        "{\"status\":\"ok\",\"name\":\"Discord Manager\",\"guildId\":\""
                                + jsonEscape(guildId)
                                + "\"}"
                );
            }
        };
        Tomcat.addServlet(context, "communityStatus", statusServlet);
        context.addServletMappingDecoded("/", "communityStatus");
        context.addServletMappingDecoded("/healthz", "communityStatus");
        context.addServletMappingDecoded("/readyz", "communityStatus");
    }

    private static Path createBaseDirectory() {
        try {
            return Files.createTempDirectory("community-agent-http-");
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create Discord Manager HTTP base directory.", exception);
        }
    }

    private static void closeQuietly(
            McpSyncServer agentServer,
            McpSyncServer operatorServer,
            HttpServletStreamableServerTransportProvider agentTransport,
            HttpServletStreamableServerTransportProvider operatorTransport,
            Tomcat tomcat
    ) {
        try {
            operatorServer.close();
        } catch (RuntimeException ignored) {
        }
        try {
            agentServer.close();
        } catch (RuntimeException ignored) {
        }
        try {
            tomcat.destroy();
        } catch (Exception ignored) {
        }
        try {
            operatorTransport.close();
        } catch (RuntimeException ignored) {
        }
        try {
            agentTransport.close();
        } catch (RuntimeException ignored) {
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            deleteRecursively(path);
        } catch (RuntimeException ignored) {
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete Discord Manager HTTP base directory.", exception);
        }
    }

    private static RuntimeException appendFailure(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
