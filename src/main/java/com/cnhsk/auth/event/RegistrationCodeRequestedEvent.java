package com.cnhsk.auth.event;

/** In-memory request to email a one-time registration code. */
public final class RegistrationCodeRequestedEvent {

    private final String recipient;
    private final String code;

    public RegistrationCodeRequestedEvent(String recipient, String code) {
        this.recipient = requireValue(recipient, "recipient");
        this.code = requireValue(code, "code");
    }

    public String getRecipient() {
        return recipient;
    }

    public String getCode() {
        return code;
    }

    private static String requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value;
    }
}
