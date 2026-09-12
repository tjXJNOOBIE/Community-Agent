package org.tavall.community.http;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Set;

/** Machine-owned host, CORS, and bearer-token boundary for a Community Agent MCP surface. */
public final class CommunityHttpSecurityFilter implements Filter {
    private final String expectedToken;
    private final Set<String> allowedHosts;
    private final Set<String> allowedOrigins;

    public CommunityHttpSecurityFilter(
            String expectedToken,
            Set<String> allowedHosts,
            Set<String> allowedOrigins
    ) {
        this.expectedToken = requireText(expectedToken, "expectedToken");
        this.allowedHosts = Set.copyOf(Objects.requireNonNull(allowedHosts, "allowedHosts"));
        this.allowedOrigins = Set.copyOf(Objects.requireNonNull(allowedOrigins, "allowedOrigins"));
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            throw new ServletException("Community Agent MCP requires HTTP transport.");
        }

        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("Access-Control-Allow-Headers", "authorization,content-type,mcp-protocol-version");
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        applyCors(httpRequest, httpResponse);

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (!hostAllowed(httpRequest)) {
            reject(httpResponse, HttpServletResponse.SC_FORBIDDEN, "host is not allowed");
            return;
        }
        if (!authorized(httpRequest)) {
            httpResponse.setHeader("WWW-Authenticate", "Bearer");
            reject(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "authenticated Discord Manager access is required");
            return;
        }
        chain.doFilter(request, response);
    }

    private void applyCors(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (allowedOrigins.contains("*")) {
            response.setHeader("Access-Control-Allow-Origin", "*");
            return;
        }
        if (origin != null && allowedOrigins.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
        }
    }

    private boolean hostAllowed(HttpServletRequest request) {
        if (allowedHosts.isEmpty() || allowedHosts.contains("*")) {
            return true;
        }
        String serverName = request.getServerName();
        return serverName != null && allowedHosts.contains(serverName);
    }

    private boolean authorized(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        String provided = authorization.substring("Bearer ".length()).trim();
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=utf-8");
        response.getWriter().write("{\"error\":\"" + escape(message) + "\"}");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
