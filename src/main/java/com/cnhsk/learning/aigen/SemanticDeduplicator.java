package com.cnhsk.learning.aigen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.aigen.embedding.EmbeddingEngine;
import com.cnhsk.learning.aigen.embedding.QuestionEmbeddingStore;
import com.cnhsk.learning.aigen.embedding.QuestionEmbeddingStore.MissingVector;
import com.cnhsk.learning.aigen.embedding.QuestionEmbeddingStore.StoredVector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prepares sentence vectors for the meaning check. Model calls never run inside a database transaction, and
 * every read or write runs in its own short transaction so no pooled connection waits on the model.
 */
@Component
public class SemanticDeduplicator {
    private static final Logger log = LoggerFactory.getLogger(SemanticDeduplicator.class);
    static final int BACKFILL_LIMIT = 500;
    private final EmbeddingEngine engine;
    private final QuestionEmbeddingStore vectors;
    private final ObjectMapper mapper;
    private final AiGenerateConfig config;
    private final TransactionTemplate reads;
    private final TransactionTemplate writes;

    public SemanticDeduplicator(EmbeddingEngine engine, QuestionEmbeddingStore vectors, ObjectMapper mapper,
            AiGenerateConfig config, PlatformTransactionManager transactions) {
        this.engine = engine;
        this.vectors = vectors;
        this.mapper = mapper;
        this.config = config;
        reads = new TransactionTemplate(transactions);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reads.setReadOnly(true);
        writes = new TransactionTemplate(transactions);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * When generation is switched on, loads the model in the background after start-up so the admin page
     * reports the real state instead of waiting for the first run that needs it.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (config.systemUserId() == 0) return;
        Thread loader = new Thread(() -> {
            if (!engine.prepare()) log.warn("Duplicate meaning check not ready after start-up");
        }, "embedding-warmup");
        loader.setDaemon(true);
        loader.start();
    }

    /** Loads the model when needed; false means generation must pause. */
    public boolean prepare() { return engine.prepare(); }

    public boolean ready() { return engine.ready(); }

    /**
     * Embeds the stems found in the provider output and reads the node's stored vectors. Output that is not
     * a questions document yields no stems; the validator rejects it afterwards.
     */
    public SemanticIndex index(long knowledgePointId, String output, int thresholdPerMille) {
        Map<String, String> stems = new LinkedHashMap<>();
        for (String stem : stems(output)) stems.putIfAbsent(AiQuestionValidator.fingerprint(stem), stem);
        List<String> keys = new ArrayList<>(stems.keySet());
        List<float[]> embedded = engine.embed(new ArrayList<>(stems.values()));
        Map<String, float[]> byStem = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) byStem.put(keys.get(i), embedded.get(i));
        List<StoredVector> stored = reads.execute(status -> vectors.forKnowledgePoint(knowledgePointId, engine.modelId()));
        return new SemanticIndex(true, thresholdPerMille / 1000.0, engine.modelId(), byStem,
                stored == null ? List.of() : stored);
    }

    /** Embeds up to {@link #BACKFILL_LIMIT} stored questions that have no vector yet; returns how many. */
    public int backfill(Collection<Long> priorityPoints) {
        List<MissingVector> missing = reads.execute(status ->
                vectors.missing(engine.modelId(), priorityPoints, BACKFILL_LIMIT));
        if (missing == null || missing.isEmpty()) return 0;
        List<float[]> embedded = engine.embed(missing.stream().map(MissingVector::stem).toList());
        Map<Long, float[]> rows = new LinkedHashMap<>();
        for (int i = 0; i < missing.size(); i++) rows.put(missing.get(i).questionId(), embedded.get(i));
        writes.executeWithoutResult(status -> vectors.save(rows, engine.modelId()));
        return rows.size();
    }

    private List<String> stems(String output) {
        List<String> result = new ArrayList<>();
        if (output == null) return result;
        try {
            JsonNode root = mapper.readTree(output);
            if (root == null || !root.path("questions").isArray()) return result;
            for (JsonNode question : root.get("questions")) {
                if (question.path("stem").isTextual() && !question.get("stem").textValue().isBlank()) {
                    result.add(question.get("stem").textValue());
                }
            }
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return List.of();
        }
        return result;
    }
}
