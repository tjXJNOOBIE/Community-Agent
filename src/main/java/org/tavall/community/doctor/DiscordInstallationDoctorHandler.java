package org.tavall.community.doctor;

import com.fasterxml.jackson.databind.JsonNode;
import org.tavall.community.config.CommunityAgentConfiguration;
import org.tavall.community.discord.DiscordGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Verifies Discord authentication/access and reports machine trust configuration. */
public final class DiscordInstallationDoctorHandler {
    private final CommunityAgentConfiguration configuration;
    private final DiscordGateway discordGateway;

    public DiscordInstallationDoctorHandler(
            CommunityAgentConfiguration configuration,
            DiscordGateway discordGateway
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.discordGateway = Objects.requireNonNull(discordGateway, "discordGateway");
    }

    public DiscordInstallationDoctorResult inspect() {
        List<DiscordDoctorCheck> checks = new ArrayList<>();
        String botUserId = null;
        String guildName = null;

        try {
            JsonNode user = discordGateway.currentUser();
            botUserId = optionalText(user, "id");
            checks.add(new DiscordDoctorCheck(
                    "bot_authentication",
                    DiscordDoctorCheck.Status.PASS,
                    "Discord bot token authenticated."
            ));
        } catch (RuntimeException exception) {
            checks.add(new DiscordDoctorCheck(
                    "bot_authentication",
                    DiscordDoctorCheck.Status.FAIL,
                    safeMessage(exception)
            ));
        }

        try {
            JsonNode guild = discordGateway.guild();
            guildName = optionalText(guild, "name");
            checks.add(new DiscordDoctorCheck(
                    "guild_access",
                    DiscordDoctorCheck.Status.PASS,
                    "Bot can access the configured guild"
                            + (guildName == null ? "." : " (" + guildName + ").")
            ));
        } catch (RuntimeException exception) {
            checks.add(new DiscordDoctorCheck(
                    "guild_access",
                    DiscordDoctorCheck.Status.FAIL,
                    safeMessage(exception)
            ));
        }

        checks.add(new DiscordDoctorCheck(
                "message_content_intent",
                DiscordDoctorCheck.Status.WARN,
                "Enable Message Content Intent in the Discord Developer Portal."
        ));
        boolean trustedPromptingEnabled = configuration.trustedDiscordInteraction().enabled();
        checks.add(new DiscordDoctorCheck(
                "trusted_prompting",
                trustedPromptingEnabled ? DiscordDoctorCheck.Status.WARN : DiscordDoctorCheck.Status.PASS,
                trustedPromptingEnabled
                        ? "Trusted Discord prompting is explicitly enabled by machine configuration."
                        : "Discord messages remain observation-only by default."
        ));

        boolean healthy = checks.stream().noneMatch(check -> check.status() == DiscordDoctorCheck.Status.FAIL);
        return new DiscordInstallationDoctorResult(
                healthy,
                configuration.guildId(),
                guildName,
                configuration.applicationId(),
                botUserId,
                checks
        );
    }

    private static String optionalText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
