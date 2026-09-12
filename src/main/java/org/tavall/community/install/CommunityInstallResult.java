package org.tavall.community.install;

import java.nio.file.Path;
import java.util.Objects;

public record CommunityInstallResult(
        Path configFile,
        String inviteUrl,
        String botTokenEnvironmentVariable
) {
    public CommunityInstallResult {
        configFile = Objects.requireNonNull(configFile, "configFile").toAbsolutePath().normalize();
        inviteUrl = requireText(inviteUrl, "inviteUrl");
        botTokenEnvironmentVariable = requireText(botTokenEnvironmentVariable, "botTokenEnvironmentVariable");
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
