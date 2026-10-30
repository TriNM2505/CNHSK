package com.cnhsk.learning.content.importer.write;

/** A merged row violates the answer rules and must roll back without stopping the import. */
public final class InvalidAnswerException extends RuntimeException {
    public InvalidAnswerException() {
        super("Đáp án phải là A–D, trỏ tới phương án có nội dung; các phương án không được trùng");
    }
}
