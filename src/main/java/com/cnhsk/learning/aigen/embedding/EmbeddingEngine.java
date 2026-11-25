package com.cnhsk.learning.aigen.embedding;

import java.util.List;

/** Turns sentences into unit-length vectors whose dot product measures how close their meanings are. */
public interface EmbeddingEngine {
    /** Identifier stored with each vector so vectors of different models are never compared. */
    String modelId();

    /** True once the model is loaded; never loads anything itself. */
    boolean ready();

    /** Loads the model when it is not loaded yet; returns false instead of throwing when it cannot. */
    boolean prepare();

    /**
     * One vector per text, in order.
     *
     * @throws EmbeddingUnavailableException when the model is not usable
     */
    List<float[]> embed(List<String> texts);
}
