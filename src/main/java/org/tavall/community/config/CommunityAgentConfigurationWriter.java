package org.tavall.community.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Writes machine-owned configuration without ever persisting the resolved Discord bot token. */
public final class CommunityAgentConfigurationWriter {
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");

    private final ObjectMapper objectMapper;

    public CommunityAgentConfigurationWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public void write(Path file, CommunityAgentConfiguration configuration) {
        Path safeFile = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        CommunityAgentConfiguration safeConfiguration = Objects.requireNonNull(configuration, "configuration");
        try {
            Path parent = safeFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
                setPermissionsIfSupported(parent, DIRECTORY_PERMISSIONS);
            }
            Files.writeString(
                    safeFile,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(toJson(safeConfiguration))
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );
            setPermissionsIfSupported(safeFile, FILE_PERMISSIONS);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write Community Agent configuration: " + safeFile, exception);
        }
    }

    private ObjectNode toJson(CommunityAgentConfiguration configuration) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", 1);
        root.put("guildId", configuration.guildId());
        root.put("applicationId", configuration.applicationId());
        root.put("discordBotTokenEnvironmentVariable", configuration.discordBotTokenEnvironmentVariable());
        if (!configuration.modelId().isBlank()) {
            root.put("modelId", configuration.modelId());
        }
        root.put("dataDirectory", configuration.dataDirectory().toString());

        ObjectNode control = root.putObject("control");
        control.put("operatorToken", configuration.control().operatorToken());
        control.put("internalAgentToken", configuration.control().internalAgentToken());
        control.put("proposalSigningSecret", configuration.control().proposalSigningSecret());

        ObjectNode autonomy = root.putObject("autonomy");
        autonomy.put("defaultMode", configuration.autonomy().defaultMode().name());
        ObjectNode overrides = autonomy.putObject("overrides");
        for (Map.Entry<String, CommunityAutonomyMode> entry : configuration.autonomy().overrides().entrySet()) {
            overrides.put(entry.getKey(), entry.getValue().name());
        }

        ObjectNode web = root.putObject("web");
        web.put("host", configuration.web().host());
        web.put("port", configuration.web().port());
        writeStrings(web.putArray("allowedHosts"), configuration.web().allowedHosts());
        writeStrings(web.putArray("allowedOrigins"), configuration.web().allowedOrigins());

        ObjectNode trusted = root.putObject("trustedDiscordInteraction");
        trusted.put("enabled", configuration.trustedDiscordInteraction().enabled());
        writeStrings(trusted.putArray("userIds"), configuration.trustedDiscordInteraction().userIds());
        writeStrings(trusted.putArray("roleIds"), configuration.trustedDiscordInteraction().roleIds());
        writeStrings(trusted.putArray("channelIds"), configuration.trustedDiscordInteraction().channelIds());
        trusted.put("requireMention", configuration.trustedDiscordInteraction().requireMention());
        return root;
    }

    private static void writeStrings(ArrayNode target, Set<String> values) {
        values.stream().sorted().forEach(target::add);
    }

    private static void setPermissionsIfSupported(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows and non-POSIX filesystems do not expose POSIX mode bits.
        }
    }
}
