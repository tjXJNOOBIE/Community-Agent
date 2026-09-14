package org.tavall.community.install;

import org.tavall.community.config.CommunityAgentConfiguration;
import org.tavall.community.config.CommunityAgentConfigurationWriter;
import org.tavall.community.config.CommunityAgentDefaultConfigurationBuilder;
import org.tavall.community.config.CommunityAgentPathResolver;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Creates the secure machine configuration and Discord bot invite for a fresh install. */
public final class CommunityAgentInstallHandler {
    private static final long DISCORD_PERMISSIONS = 268_667_905L;

    private final CommunityAgentDefaultConfigurationBuilder configurationBuilder;
    private final CommunityAgentConfigurationWriter configurationWriter;
    private final CommunityAgentPathResolver paths;

    public CommunityAgentInstallHandler(
            CommunityAgentDefaultConfigurationBuilder configurationBuilder,
            CommunityAgentConfigurationWriter configurationWriter,
            CommunityAgentPathResolver paths
    ) {
        this.configurationBuilder = Objects.requireNonNull(configurationBuilder, "configurationBuilder");
        this.configurationWriter = Objects.requireNonNull(configurationWriter, "configurationWriter");
        this.paths = Objects.requireNonNull(paths, "paths");
    }

    public CommunityInstallResult install(
            String guildId,
            String applicationId,
            boolean force
    ) {
        Path configFile = paths.configFile();
        if (Files.exists(configFile) && !force) {
            throw new IllegalStateException(
                    "Config already exists at " + configFile + "; use --force to replace it."
            );
        }

        CommunityAgentConfiguration configuration = configurationBuilder.build(
                guildId,
                applicationId,
                paths.dataDirectory()
        );
        configurationWriter.write(configFile, configuration);
        String inviteUrl = "https://discord.com/oauth2/authorize?client_id="
                + URLEncoder.encode(applicationId, StandardCharsets.UTF_8)
                + "&scope=bot%20applications.commands&permissions="
                + DISCORD_PERMISSIONS;
        return new CommunityInstallResult(
                configFile,
                inviteUrl,
                configuration.discordBotTokenEnvironmentVariable()
        );
    }
}
