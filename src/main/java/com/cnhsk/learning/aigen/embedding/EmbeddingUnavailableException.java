package com.cnhsk.learning.aigen.embedding;

/** The sentence model cannot be loaded or run; generation pauses instead of saving unchecked questions. */
public class EmbeddingUnavailableException extends RuntimeException {
    public EmbeddingUnavailableException() { super("EMBEDDING_UNAVAILABLE"); }
}
