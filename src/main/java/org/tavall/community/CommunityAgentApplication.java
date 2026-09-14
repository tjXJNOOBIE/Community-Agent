package org.tavall.community;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.ai.agent.AIAgentExecutionResult;
import org.tavall.ai.agent.AIAgentExecutionStatus;
import org.tavall.community.config.CommunityAgentConfiguration;
import org.tavall.community.config.CommunityAgentConfigurationReader;
import org.tavall.community.config.CommunityAgentConfigurationWriter;
import org.tavall.community.config.CommunityAgentDefaultConfigurationBuilder;
import org.tavall.community.config.CommunityAgentPathResolver;
import org.tavall.community.discord.DiscordGateway;
import org.tavall.community.discord.DiscordHttpGateway;
import org.tavall.community.doctor.DiscordDoctorCheck;
import org.tavall.community.doctor.DiscordInstallationDoctorHandler;
import org.tavall.community.doctor.DiscordInstallationDoctorResult;
import org.tavall.community.install.CommunityAgentInstallHandler;
import org.tavall.community.install.CommunityInstallResult;
import org.tavall.community.runtime.CommunityApplicationBootstrap;
import org.tavall.community.runtime.CommunityApplicationRuntime;

import java.io.Console;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
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
            System.exit(130);
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            System.err.println(message == null || message.isBlank()
                    ? exception.getClass().getSimpleName()
                    : message);
            System.exit(1);
        }
    }

    static void run(List<String> arguments, Map<String, String> environment) throws InterruptedException {
        CommunityAgentPathResolver paths = new CommunityAgentPathResolver(environment);
        Path configurationPath = configurationPath(arguments, paths);
        List<String> commandArguments = withoutOption(arguments, "--config");
        String command = commandArguments.isEmpty() ? "" : commandArguments.getFirst();

        if ("install".equals(command)) {
            install(commandArguments.subList(1, commandArguments.size()), environment, paths);
            return;
        }
        if ("doctor".equals(command)) {
            doctor(commandArguments.subList(1, commandArguments.size()), environment, configurationPath);
            return;
        }
        if ("serve".equals(command)) {
            serve(configurationPath, environment);
            return;
        }

        String request = String.join(" ", commandArguments).trim();
        if (request.isEmpty()) {
            throw new IllegalArgumentException(
                    "Provide a Discord Manager request or use install, doctor, or serve."
            );
        }
        oneShot(configurationPath, environment, request);
    }

    private static void install(
            List<String> arguments,
            Map<String, String> environment,
            CommunityAgentPathResolver paths
    ) {
        String guildId = promptIfMissing(option(arguments, "--guild-id"), "Discord server/guild ID");
        String applicationId = promptIfMissing(option(arguments, "--application-id"), "Discord application ID");
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CommunityAgentInstallHandler handler = new CommunityAgentInstallHandler(
                new CommunityAgentDefaultConfigurationBuilder(environment),
                new CommunityAgentConfigurationWriter(objectMapper),
                paths
        );
        CommunityInstallResult result = handler.install(
                guildId,
                applicationId,
                arguments.contains("--force")
        );

        System.out.println("Config: " + result.configFile());
        System.out.println("Invite bot: " + result.inviteUrl());
        System.out.println("Set the bot token in " + result.botTokenEnvironmentVariable() + ".");
        System.out.println("Set COMMUNITY_AGENT_STRANDS_NODE to the absolute Node executable path.");
        System.out.println("Set COMMUNITY_AGENT_STRANDS_ENTRYPOINT to the installed strands-bridge dist/mcp/main.js.");
        System.out.println("Discord prompting is disabled by default. Trusted users/channels/roles must be enabled from the machine/operator control layer.");
        System.out.println("Verify setup with: community-agent doctor");
        System.out.println("Then start with: community-agent serve");
        System.out.println("First operator request: community-agent \"Analyze my Discord server.\"");
    }

    private static void doctor(
            List<String> arguments,
            Map<String, String> environment,
            Path configurationPath
    ) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CommunityAgentConfiguration configuration = new CommunityAgentConfigurationReader(
                objectMapper,
                environment
        ).read(configurationPath);
        if (configuration.discordBotToken().isBlank()) {
            throw new IllegalStateException(
                    "Set " + configuration.discordBotTokenEnvironmentVariable()
                            + " before running Discord Manager doctor."
            );
        }
        DiscordGateway discordGateway = new DiscordHttpGateway(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                objectMapper,
                configuration.discordBotToken(),
                configuration.guildId()
        );
        DiscordInstallationDoctorResult result = new DiscordInstallationDoctorHandler(
                configuration,
                discordGateway
        ).inspect();

        if (arguments.contains("--json")) {
            System.out.println(prettyJson(objectMapper, result));
        } else {
            System.out.println(renderDoctor(result));
        }
        if (!result.healthy()) {
            throw new IllegalStateException("Discord Manager doctor failed.");
        }
    }

    private static String renderDoctor(DiscordInstallationDoctorResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("Discord Manager doctor: " + (result.healthy() ? "PASS" : "FAIL"));
        lines.add("Guild: " + (result.guildName() == null ? result.guildId() : result.guildName())
                + " (" + result.guildId() + ")");
        lines.add("Application: " + result.applicationId());
        if (result.botUserId() != null) {
            lines.add("Bot user: " + result.botUserId());
        }
        lines.add("");
        for (DiscordDoctorCheck check : result.checks()) {
            lines.add("[" + check.status().name() + "] " + check.key() + ": " + check.message());
        }
        return String.join(System.lineSeparator(), lines);
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

    private static String promptIfMissing(String value, String prompt) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalArgumentException(prompt + " is required when stdin is not interactive.");
        }
        String answer = console.readLine("%s: ", prompt);
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException(prompt + " must not be blank.");
        }
        return answer.trim();
    }

    private static String option(List<String> arguments, String name) {
        int index = arguments.indexOf(name);
        if (index < 0 || index + 1 >= arguments.size()) {
            return null;
        }
        return arguments.get(index + 1);
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

    private static String prettyJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize doctor result.", exception);
        }
    }
}
