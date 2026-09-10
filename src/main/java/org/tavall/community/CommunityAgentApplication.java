package org.tavall.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.ai.agent.AIAgentExecutionResult;
import org.tavall.ai.agent.AIAgentExecutionStatus;
import org.tavall.community.config.CommunityAgentPathResolver;
import org.tavall.community.runtime.CommunityApplicationBootstrap;
import org.tavall.community.runtime.CommunityApplicationRuntime;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** Java-first Discord Manager process entrypoint. */
public final class CommunityAgentApplication {
    private CommunityAgentApplication() {
    }

    public static void main(String[] args) {
        try {
            run(List.of(args), System.getenv());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Discord Manager was interrupted.");
            System.exitCode = 130;
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            System.err.println(message == null || message.isBlank()
                    ? exception.getClass().getSimpleName()
                    : message);
            System.exitCode = 1;
        }
    }

    static void run(List<String> arguments, Map<String, String> environment) throws InterruptedException {
        CommunityAgentPathResolver paths = new CommunityAgentPathResolver(environment);
        Path configurationPath = configurationPath(arguments, paths);
        List<String> commandArguments = withoutOption(arguments, "--config");
        String command = commandArguments.isEmpty() ? "" : commandArguments.getFirst();

        if ("serve".equals(command)) {
            serve(configurationPath, environment);
            return;
        }
        if ("install".equals(command) || "doctor".equals(command)) {
            throw new IllegalArgumentException(
                    command + " has not yet been cut over to Java; use the existing installer/doctor during migration"
            );
        }

        List<String> requestArguments = commandArguments.isEmpty()
                ? List.of()
                : commandArguments;
        String request = String.join(" ", requestArguments).trim();
        if (request.isEmpty()) {
            throw new IllegalArgumentException("Provide a Discord Manager request or use the serve command.");
        }
        oneShot(configurationPath, environment, request);
    }

    private static void serve(Path configurationPath, Map<String, String> environment) throws InterruptedException {
        CommunityApplicationRuntime runtime = CommunityApplicationBootstrap.start(configurationPath, environment);
        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown::countDown, "community-agent-shutdown"));
        System.out.println("Discord Manager Java runtime listening on port " + runtime.port());
        try {
            shutdown.await();
        } finally {
            runtime.close();
        }
    }

    private static void oneShot(
            Path configurationPath,
            Map<String, String> environment,
            String request
    ) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        try (CommunityApplicationRuntime runtime = CommunityApplicationBootstrap.start(configurationPath, environment)) {
            AIAgentExecutionResult result = runtime.invoke(request);
            if (result.status() != AIAgentExecutionStatus.COMPLETED) {
                throw new IllegalStateException(result.errorMessage() == null
                        ? result.output().toString()
                        : result.errorMessage());
            }
            String text = result.output().path("text").asText("");
            System.out.println(text.isBlank() ? result.output().toPrettyString() : text);
        }
    }

    private static Path configurationPath(
            List<String> arguments,
            CommunityAgentPathResolver paths
    ) {
        int index = arguments.indexOf("--config");
        if (index < 0) {
            return paths.configFile();
        }
        if (index + 1 >= arguments.size() || arguments.get(index + 1).isBlank()) {
            throw new IllegalArgumentException("--config requires a path");
        }
        return Path.of(arguments.get(index + 1)).toAbsolutePath().normalize();
    }

    private static List<String> withoutOption(List<String> arguments, String optionName) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            String value = arguments.get(index);
            if (optionName.equals(value)) {
                index++;
                continue;
            }
            result.add(value);
        }
        return List.copyOf(result);
    }
}
