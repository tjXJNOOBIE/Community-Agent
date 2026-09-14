package org.tavall.community.doctor;

public record DiscordDoctorCheck(String key, Status status, String message) {
    public DiscordDoctorCheck {
        key = requireText(key, "key");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        message = requireText(message, "message");
    }

    public enum Status {
        PASS,
        WARN,
        FAIL
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
