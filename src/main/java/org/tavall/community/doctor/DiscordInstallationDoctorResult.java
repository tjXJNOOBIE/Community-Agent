package org.tavall.community.doctor;

import java.util.List;

public record DiscordInstallationDoctorResult(
        boolean healthy,
        String guildId,
        String guildName,
        String applicationId,
        String botUserId,
        List<DiscordDoctorCheck> checks
) {
    public DiscordInstallationDoctorResult {
        guildId = requireText(guildId, "guildId");
        applicationId = requireText(applicationId, "applicationId");
        guildName = normalize(guildName);
        botUserId = normalize(botUserId);
        checks = List.copyOf(checks == null ? List.of() : checks);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
