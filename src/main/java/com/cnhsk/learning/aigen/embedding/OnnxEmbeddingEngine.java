package com.cnhsk.learning.aigen.embedding;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Multilingual sentence model run in process with ONNX Runtime. The model is loaded lazily, outside
 * application start, and a failed load is retried on the next {@link #prepare()} call.
 */
@Component
@Profile("!test")
public class OnnxEmbeddingEngine implements EmbeddingEngine {
    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingEngine.class);
    private static final int BATCH = 32;
    private final EmbeddingProperties properties;
    private final ModelFiles files;
    private final Object loading = new Object();
    private volatile Loaded loaded;

    @Autowired
    public OnnxEmbeddingEngine(EmbeddingProperties properties) { this(properties, ModelFiles.create()); }

    OnnxEmbeddingEngine(EmbeddingProperties properties, ModelFiles files) {
        this.properties = properties;
        this.files = files;
    }

    @Override public String modelId() { return properties.getModelId(); }

    @Override public boolean ready() { return loaded != null; }

    @Override
    public boolean prepare() {
        if (loaded != null) return true;
        synchronized (loading) {
            if (loaded != null) return true;
            try {
                loaded = load();
                log.info("Sentence embedding model loaded: {}", properties.getModelId());
                return true;
            } catch (Exception | LinkageError exception) {
                log.warn("Sentence embedding model unavailable: {}", exception.getClass().getSimpleName());
                return false;
            }
        }
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        if (!prepare()) throw new EmbeddingUnavailableException();
        // Shares the lock with close() so shutdown never frees native memory under a running embedding.
        synchronized (loading) {
            Loaded model = loaded;
            if (model == null) throw new EmbeddingUnavailableException();
            List<float[]> result = new ArrayList<>(texts.size());
            try {
                for (int start = 0; start < texts.size(); start += BATCH) {
                    result.addAll(model.embed(texts.subList(start, Math.min(texts.size(), start + BATCH))));
                }
            } catch (OrtException | RuntimeException exception) {
                log.warn("Sentence embedding failed: {}", exception.getClass().getSimpleName());
                throw new EmbeddingUnavailableException();
            }
            return result;
        }
    }

    private Loaded load() throws Exception {
        Path folder = properties.getCacheDir().resolve(properties.getModelId());
        Path model = files.ensure(folder.resolve("model.onnx"), properties.getModelUrl(), properties.getModelSha256());
        Path vocabulary = files.ensure(folder.resolve("tokenizer.json"), properties.getTokenizerUrl(),
                properties.getTokenizerSha256());
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.builder().optTokenizerPath(vocabulary)
                .optMaxLength(properties.getMaxTokens()).optTruncation(true).optPadding(true).build();
        OrtEnvironment environment = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            OrtSession session = environment.createSession(model.toString(), options);
            return new Loaded(environment, session, tokenizer, session.getInputNames());
        } catch (OrtException | RuntimeException exception) {
            tokenizer.close();
            throw exception;
        }
    }

    @PreDestroy
    public void close() {
        synchronized (loading) {
            Loaded model = loaded;
            loaded = null;
            if (model == null) return;
            try { model.session().close(); } catch (OrtException ignored) { /* Process is ending. */ }
            model.tokenizer().close();
        }
    }

    private record Loaded(OrtEnvironment environment, OrtSession session, HuggingFaceTokenizer tokenizer,
            Set<String> inputs) {
        List<float[]> embed(List<String> texts) throws OrtException {
            Encoding[] encodings = tokenizer.batchEncode(texts);
            int rows = encodings.length;
            int width = 0;
            for (Encoding encoding : encodings) width = Math.max(width, encoding.getIds().length);
            long[][] ids = new long[rows][width];
            long[][] mask = new long[rows][width];
            long[][] types = new long[rows][width];
            for (int row = 0; row < rows; row++) {
                long[] rowIds = encodings[row].getIds();
                long[] rowMask = encodings[row].getAttentionMask();
                long[] rowTypes = encodings[row].getTypeIds();
                System.arraycopy(rowIds, 0, ids[row], 0, rowIds.length);
                System.arraycopy(rowMask, 0, mask[row], 0, rowMask.length);
                System.arraycopy(rowTypes, 0, types[row], 0, rowTypes.length);
            }
            Map<String, OnnxTensor> feed = new HashMap<>();
            try {
                feed.put("input_ids", OnnxTensor.createTensor(environment, ids));
                feed.put("attention_mask", OnnxTensor.createTensor(environment, mask));
                if (inputs.contains("token_type_ids")) feed.put("token_type_ids", OnnxTensor.createTensor(environment, types));
                try (OrtSession.Result output = session.run(feed)) {
                    float[][][] hidden = (float[][][]) output.get(0).getValue();
                    List<float[]> vectors = new ArrayList<>(rows);
                    for (int row = 0; row < rows; row++) vectors.add(meanPool(hidden[row], mask[row]));
                    return vectors;
                }
            } finally {
                feed.values().forEach(OnnxTensor::close);
            }
        }
    }

    /** Average of the real (unpadded) token vectors, scaled to unit length so a dot product is a cosine. */
    static float[] meanPool(float[][] tokens, long[] mask) {
        int dimensions = tokens[0].length;
        float[] sum = new float[dimensions];
        int count = 0;
        for (int token = 0; token < tokens.length; token++) {
            if (mask[token] == 0) continue;
            count++;
            for (int d = 0; d < dimensions; d++) sum[d] += tokens[token][d];
        }
        double norm = 0;
        for (int d = 0; d < dimensions; d++) {
            sum[d] /= Math.max(1, count);
            norm += (double) sum[d] * sum[d];
        }
        norm = Math.sqrt(norm);
        if (norm > 0) for (int d = 0; d < dimensions; d++) sum[d] /= (float) norm;
        return sum;
    }
}
