package com.cnhsk.learning.aigen.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OnnxEmbeddingEngineTest {
    private static final String UNREACHABLE = "http://127.0.0.1:9/model";
    private static final String MODEL_ID = "paraphrase-multilingual-MiniLM-L12-v2-int8";
    private static final String MODEL_SHA = "66fc00f5f29afcaff34092e1bdd20008ca3918265a82fb9695a551e510cc4ebc";
    private static final String TOKENIZER_SHA = "b60b6b43406a48bf3638526314f3d232d97058bc93472ff2de930d43686fa441";

    /** Tests run with the stub engine, so only this check proves Spring can build the real one at startup. */
    @Test
    void springCreatesTheEngineFromItsPropertiesWithoutLoadingTheModel(@TempDir Path cache) {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(EmbeddingProperties.class, () -> properties(cache, UNREACHABLE));
            context.registerBean(OnnxEmbeddingEngine.class);
            context.refresh();
            assertThat(context.getBean(OnnxEmbeddingEngine.class).ready()).isFalse();
        }
    }

    @Test
    void missingFilesWithoutNetworkLeaveTheEngineUnavailableWithoutThrowing(@TempDir Path cache) {
        var engine = new OnnxEmbeddingEngine(properties(cache, UNREACHABLE));
        assertThat(engine.prepare()).isFalse();
        assertThat(engine.ready()).isFalse();
        assertThatThrownBy(() -> engine.embed(List.of("你好"))).isInstanceOf(EmbeddingUnavailableException.class);
        assertThat(engine.embed(List.of())).isEmpty();
    }

    @Test
    void aFileWithAnotherDigestIsDeletedAndNeverLoaded(@TempDir Path cache) throws Exception {
        Path folder = Files.createDirectories(cache.resolve(MODEL_ID));
        Path tampered = Files.writeString(folder.resolve("model.onnx"), "not the published model", StandardCharsets.UTF_8);
        var engine = new OnnxEmbeddingEngine(properties(cache, UNREACHABLE));
        assertThat(engine.prepare()).isFalse();
        assertThat(tampered).doesNotExist();
        assertThat(folder.resolve("model.onnx.part")).doesNotExist();
    }

    @Test
    void verifiedLocalCopyIsKeptWithoutDownloading(@TempDir Path cache) throws Exception {
        Path file = Files.writeString(cache.resolve("kept.bin"), "published bytes", StandardCharsets.UTF_8);
        String digest = ModelFiles.digest(file);
        assertThat(ModelFiles.create().ensure(file, UNREACHABLE, digest)).isEqualTo(file);
        assertThat(Files.readString(file)).isEqualTo("published bytes");
    }

    @Test
    void aStalledDownloadBodyFailsAtTheDeadlineInsteadOfHanging(@TempDir Path cache) throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model", exchange -> {
            exchange.sendResponseHeaders(200, 1_000_000);
            exchange.getResponseBody().write(new byte[16]);
            exchange.getResponseBody().flush();
            try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var files = new ModelFiles(java.net.http.HttpClient.newHttpClient(), java.time.Duration.ofMillis(800));
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/model";
            long started = System.nanoTime();
            assertThatThrownBy(() -> files.ensure(cache.resolve("model.onnx"), url, MODEL_SHA))
                    .isInstanceOf(java.io.IOException.class).hasMessageContaining("deadline");
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(10));
            assertThat(cache.resolve("model.onnx")).doesNotExist();
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    void meanPoolingIgnoresPaddingAndReturnsAUnitVector() {
        float[][] tokens = {{3, 0}, {1, 0}, {100, 100}};
        float[] pooled = OnnxEmbeddingEngine.meanPool(tokens, new long[] {1, 1, 0});
        assertThat(pooled[0]).isEqualTo(1f);
        assertThat(pooled[1]).isZero();
    }

    /**
     * Runs the real model when it is already in the local cache (the leader's machine after first use);
     * skipped elsewhere so the suite never downloads 120 MB.
     */
    @Test
    void realModelPlacesParaphrasesCloserThanUnrelatedSentences() {
        Path cache = Path.of(System.getProperty("user.home"), ".cnhsk", "models");
        assumeThat(cache.resolve(MODEL_ID).resolve("model.onnx")).exists();
        assumeThat(cache.resolve(MODEL_ID).resolve("tokenizer.json")).exists();
        var engine = new OnnxEmbeddingEngine(properties(cache, UNREACHABLE));
        try {
            assertThat(engine.prepare()).isTrue();
            List<float[]> vectors = engine.embed(List.of(
                    "他每天坐公交车去学校。", "他天天乘巴士上学。", "我喜欢喝绿茶。",
                    "我昨天去了北京。", "昨天我去北京了。", "这本书很有意思。"));
            assertThat(vectors).allSatisfy(vector -> assertThat(vector).hasSize(384));
            double busParaphrase = dot(vectors.get(0), vectors.get(1));
            double busUnrelated = dot(vectors.get(0), vectors.get(2));
            double beijingParaphrase = dot(vectors.get(3), vectors.get(4));
            double beijingUnrelated = dot(vectors.get(3), vectors.get(5));
            System.out.printf("Embedding cosine: bus paraphrase %.3f, unrelated %.3f; Beijing paraphrase %.3f, unrelated %.3f%n",
                    busParaphrase, busUnrelated, beijingParaphrase, beijingUnrelated);
            assertThat(busParaphrase).isGreaterThan(busUnrelated + 0.2);
            assertThat(beijingParaphrase).isGreaterThan(beijingUnrelated + 0.2);
            assertThat(dot(vectors.get(0), vectors.get(0))).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-3));
            // Drill templates that keep the frame but change the content; printed to calibrate the threshold.
            List<float[]> drills = engine.embed(List.of(
                    "他昨天___北京。", "他明天___上海。", "我___喜欢喝茶。", "我___喜欢喝咖啡。",
                    "她每天七点起床。", "她每天八点起床。", "这个苹果多少钱？", "这件衣服多少钱？"));
            for (int i = 0; i < drills.size(); i += 2) {
                System.out.printf("Embedding cosine drill pair %d: %.3f%n", i / 2 + 1, dot(drills.get(i), drills.get(i + 1)));
            }
        } finally {
            engine.close();
        }
    }

    private static double dot(float[] left, float[] right) {
        double sum = 0;
        for (int i = 0; i < left.length; i++) sum += (double) left[i] * right[i];
        return sum;
    }

    private static EmbeddingProperties properties(Path cache, String url) {
        var properties = new EmbeddingProperties();
        properties.setModelId(MODEL_ID);
        properties.setCacheDir(cache);
        properties.setModelUrl(url);
        properties.setTokenizerUrl(url);
        properties.setModelSha256(MODEL_SHA);
        properties.setTokenizerSha256(TOKENIZER_SHA);
        properties.setMaxTokens(128);
        return properties;
    }
}
