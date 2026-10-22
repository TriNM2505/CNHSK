package com.cnhsk.auth.event;

/** In-memory request to send an email verification link after the publisher transaction commits. */
public final class EmailVerificationRequestedEvent {

    private final String recipient;
    private final String token;

    public EmailVerificationRequestedEvent(String recipient, String token) {
        this.recipient = requireValue(recipient, "recipient");
        this.token = requireValue(token, "token");
    }

    public String getRecipient() {
        return recipient;
    }

    public String getToken() {
        return token;
    }

    private static String requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value;
    }
}
