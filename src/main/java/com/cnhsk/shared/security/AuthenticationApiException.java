package com.cnhsk.shared.security;

import java.util.Map;

import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.security.core.AuthenticationException;

/** Authentication failure metadata for the SecurityConfig entry point (T-10). */
public class AuthenticationApiException extends AuthenticationException {

    private final ErrorCode errorCode;
    private final Map<String, Object> extra;

    public AuthenticationApiException(ErrorCode errorCode, String message, Map<String, Object> extra) {
        super(message);
        this.errorCode = errorCode;
        this.extra = extra == null ? Map.of() : Map.copyOf(extra);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }
}
