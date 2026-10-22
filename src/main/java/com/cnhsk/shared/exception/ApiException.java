package com.cnhsk.shared.exception;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;

/** An expected API failure with a contract-defined code and HTTP status. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final Map<String, Object> extra;

    public ApiException(ErrorCode code, HttpStatus status, String message, Map<String, Object> extra) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.status = Objects.requireNonNull(status, "status");
        this.extra = extra == null ? Map.of() : Map.copyOf(extra);
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }
}
