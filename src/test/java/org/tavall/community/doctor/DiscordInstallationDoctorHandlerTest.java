package org.tavall.community.doctor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.community.config.CommunityAgentConfiguration;
import org.tavall.community.config.CommunityAutonomyMode;
import org.tavall.community.discord.DiscordGateway;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordInstallationDoctorHandlerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void reportsHealthyAuthenticationAndGuildAccess() {
        ObjectMapper objectMapper = new ObjectMapper();
        DiscordGateway gateway = new DiscordGateway() {
            @Override
            public JsonNode guild() {
                return objectMapper.createObjectNode().put("id", "guild").put("name", "Tavall");
            }

            @Override
            public JsonNode channels() {
                return objectMapper.createArrayNode();
            }

            @Override
            public JsonNode currentUser() {
                return objectMapper.createObjectNode().put("id", "bot-user");
            }

            @Override
            public JsonNode sendMessage(String channelId, String content) {
                throw new AssertionError("doctor must not mutate Discord");
            }
        };

        DiscordInstallationDoctorResult result = new DiscordInstallationDoctorHandler(
                configuration(false),
                gateway
        ).inspect();

        assertThat(result.healthy()).isTrue();
        assertThat(result.guildName()).isEqualTo("Tavall");
        assertThat(result.botUserId()).isEqualTo("bot-user");
        assertThat(result.checks())
                .extracting(DiscordDoctorCheck::status)
                .containsExactly(
                        DiscordDoctorCheck.Status.PASS,
                        DiscordDoctorCheck.Status.PASS,
                        DiscordDoctorCheck.Status.WARN,
                        DiscordDoctorCheck.Status.PASS
                );
    }

    @Test
    void reportsFailureWithoutAttemptingMutation() {
        ObjectMapper objectMapper = new ObjectMapper();
        DiscordGateway gateway = new DiscordGateway() {
            @Override
            public JsonNode guild() {
                throw new IllegalStateException("guild denied");
            }

            @Override
            public JsonNode channels() {
                return objectMapper.createArrayNode();
            }

            @Override
            public JsonNode currentUser() {
                throw new IllegalStateException("bad token");
            }

            @Override
            public JsonNode sendMessage(String channelId, String content) {
                throw new AssertionError("doctor must not mutate Discord");
            }
        };

        DiscordInstallationDoctorResult result = new DiscordInstallationDoctorHandler(
                configuration(true),
                gateway
        ).inspect();

        assertThat(result.healthy()).isFalse();
        assertThat(result.checks())
                .extracting(DiscordDoctorCheck::status)
                .containsExactly(
                        DiscordDoctorCheck.Status.FAIL,
                        DiscordDoctorCheck.Status.FAIL,
                        DiscordDoctorCheck.Status.WARN,
                        DiscordDoctorCheck.Status.WARN
                );
    }

    private CommunityAgentConfiguration configuration(boolean trustedPrompting) {
        return new CommunityAgentConfiguration(
                "guild",
                "application",
                "COMMUNITY_AGENT_DISCORD_BOT_TOKEN",
                "token",
                "",
                tempDirectory,
                new CommunityAgentConfiguration.Control("operator", "agent", "secret"),
                new CommunityAgentConfiguration.Autonomy(CommunityAutonomyMode.PROPOSE, Map.of()),
                new CommunityAgentConfiguration.Web("127.0.0.1", 0, Set.of("127.0.0.1"), Set.of()),
                new CommunityAgentConfiguration.TrustedDiscordInteraction(
                        trustedPrompting,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        true
                ),
                Duration.ofMinutes(15)
        );
    }
}
