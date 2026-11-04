package com.cnhsk.learning.content.importer.write;

/** A safe business rejection rolls back one row while preserving its contract error. */
public final class RowWriteRejectedException extends RuntimeException {
    private final String code;
    private final String field;

    public RowWriteRejectedException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String code() { return code; }
    public String field() { return field; }
}
