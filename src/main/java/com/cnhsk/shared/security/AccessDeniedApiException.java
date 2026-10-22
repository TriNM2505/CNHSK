package com.cnhsk.shared.security;

import java.util.Map;

import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.security.access.AccessDeniedException;

/** Authorization failure metadata for the SecurityConfig access-denied handler (T-10). */
public class AccessDeniedApiException extends AccessDeniedException {

    private final ErrorCode errorCode;
    private final Map<String, Object> extra;

    public AccessDeniedApiException(ErrorCode errorCode, String message, Map<String, Object> extra) {
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
