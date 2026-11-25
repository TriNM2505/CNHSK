package com.cnhsk.learning.aigen;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.aigen.embedding.EmbeddingUnavailableException;
import com.cnhsk.learning.aigen.embedding.QuestionEmbeddingStore.StoredVector;

/**
 * Vectors prepared outside any transaction for one generated batch: the new stems and the node's stored
 * questions. {@link #off()} skips the meaning check; production batches always carry a prepared index.
 */
public record SemanticIndex(boolean enabled, double threshold, String model, Map<String, float[]> stems,
        List<StoredVector> existing) {
    public SemanticIndex {
        stems = Map.copyOf(stems);
        existing = List.copyOf(existing);
    }

    public static SemanticIndex off() { return new SemanticIndex(false, 1, "", Map.of(), List.of()); }

    /** Vector of a generated stem, looked up by the same whitespace fingerprint the validator uses. */
    public float[] vectorOf(String stem) {
        float[] vector = stems.get(AiQuestionValidator.fingerprint(stem));
        if (vector == null) throw new EmbeddingUnavailableException();
        return vector;
    }

    static double cosine(float[] left, float[] right) {
        if (left.length != right.length) return 0;
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
            leftNorm += (double) left[i] * left[i];
            rightNorm += (double) right[i] * right[i];
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : dot / Math.sqrt(leftNorm * rightNorm);
    }
}
