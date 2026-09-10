package org.tavall.community.install;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.community.config.CommunityAgentConfiguration;
import org.tavall.community.config.CommunityAgentConfigurationReader;
import org.tavall.community.config.CommunityAgentConfigurationWriter;
import org.tavall.community.config.CommunityAgentDefaultConfigurationBuilder;
import org.tavall.community.config.CommunityAgentPathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommunityAgentInstallHandlerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void installsVersionOneConfigWithoutPersistingResolvedBotToken() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Map<String, String> environment = Map.of(
                "COMMUNITY_AGENT_DATA_DIR", tempDirectory.toString(),
                "COMMUNITY_AGENT_MODEL_ID", "test-model",
                "COMMUNITY_AGENT_DISCORD_BOT_TOKEN", "must-never-be-written"
        );
        CommunityAgentPathResolver paths = new CommunityAgentPathResolver(environment);
        CommunityAgentInstallHandler handler = new CommunityAgentInstallHandler(
                new CommunityAgentDefaultConfigurationBuilder(environment),
                new CommunityAgentConfigurationWriter(objectMapper),
                paths
        );

        CommunityInstallResult result = handler.install("guild", "application", false);
        String rawConfig = Files.readString(result.configFile());
        JsonNode configJson = objectMapper.readTree(rawConfig);
        CommunityAgentConfiguration parsed = new CommunityAgentConfigurationReader(
                objectMapper,
                environment
        ).read(result.configFile());

        assertThat(configJson.path("version").asInt()).isEqualTo(1);
        assertThat(configJson.has("discordBotToken")).isFalse();
        assertThat(rawConfig).doesNotContain("must-never-be-written");
        assertThat(parsed.discordBotToken()).isEqualTo("must-never-be-written");
        assertThat(parsed.modelId()).isEqualTo("test-model");
        assertThat(result.inviteUrl()).contains("permissions=268667905");
        assertThat(result.botTokenEnvironmentVariable()).isEqualTo("COMMUNITY_AGENT_DISCORD_BOT_TOKEN");
        assertPosixPermissionsIfSupported(result.configFile());

        assertThatThrownBy(() -> handler.install("guild", "application", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("use --force");
    }

    private static void assertPosixPermissionsIfSupported(Path path) throws IOException {
        if (!Files.getFileStore(path).supportsFileAttributeView("posix")) {
            return;
        }
        PosixFileAttributes attributes = Files.readAttributes(path, PosixFileAttributes.class);
        assertThat(PosixFilePermissions.toString(attributes.permissions())).isEqualTo("rw-------");
    }
}
