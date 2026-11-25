package com.cnhsk.learning.aigen.embedding;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test engine: distinct texts get unrelated unit vectors (cosine near 0), equal texts identical ones.
 * Tests pin a vector to a text to model paraphrases, or switch the engine off.
 */
@Component
@Profile("test")
public class StubEmbeddingEngine implements EmbeddingEngine {
    public static final int DIMENSIONS = 384;
    private final Map<String, float[]> pinned = new ConcurrentHashMap<>();
    private final AtomicInteger embedded = new AtomicInteger();
    private volatile boolean available = true;
    private volatile boolean failing;
    private volatile String failingText;

    public void setAvailable(boolean available) { this.available = available; }
    /** Model loads but every embedding call fails, as when the native runtime crashes mid-run. */
    public void setFailing(boolean failing) { this.failing = failing; }
    public void pin(String text, float[] vector) { pinned.put(text, vector.clone()); }
    /** Embedding fails whenever this text is part of the call, e.g. a generated stem after the provider call. */
    public void failOn(String text) { failingText = text; }
    public int embeddedTexts() { return embedded.get(); }

    public void reset() {
        pinned.clear();
        available = true;
        failing = false;
        failingText = null;
        embedded.set(0);
    }

    @Override public String modelId() { return "stub-embedding"; }
    @Override public boolean ready() { return available; }
    @Override public boolean prepare() { return available; }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (!available || failing || (failingText != null && texts.contains(failingText))) {
            throw new EmbeddingUnavailableException();
        }
        List<float[]> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            embedded.incrementAndGet();
            float[] fixed = pinned.get(text);
            result.add(fixed != null ? fixed.clone() : random(text));
        }
        return result;
    }

    /** Unit vector built from two pinned directions: cosine with {@code axis(0)} equals {@code cosine}. */
    public static float[] withCosine(double cosine, int otherAxis) {
        float[] vector = new float[DIMENSIONS];
        vector[0] = (float) cosine;
        vector[otherAxis] = (float) Math.sqrt(Math.max(0, 1 - cosine * cosine));
        return vector;
    }

    private static float[] random(String text) {
        SplittableRandom random = new SplittableRandom(java.util.Arrays.hashCode(text.getBytes(StandardCharsets.UTF_8)));
        float[] vector = new float[DIMENSIONS];
        double norm = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = (float) (random.nextDouble() * 2 - 1);
            norm += (double) vector[i] * vector[i];
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < DIMENSIONS; i++) vector[i] *= scale;
        return vector;
    }
}
