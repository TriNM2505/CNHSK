package com.cnhsk.learning.aigen.embedding;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Keeps a verified local copy of one model file, downloading it once when it is missing or altered. */
class ModelFiles {
    private final HttpClient http;
    private final Duration deadline;

    ModelFiles(HttpClient http, Duration deadline) {
        this.http = http;
        this.deadline = deadline;
    }

    static ModelFiles create() {
        return new ModelFiles(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10)).build(), Duration.ofMinutes(10));
    }

    /**
     * Returns {@code target} once its SHA-256 equals {@code sha256}. A file with another digest is replaced;
     * a download with another digest is deleted and reported as a failure, never used.
     */
    Path ensure(Path target, String url, String sha256) throws IOException {
        if (Files.isRegularFile(target)) {
            if (sha256.equals(digest(target))) return target;
            Files.delete(target);
        }
        Files.createDirectories(target.getParent());
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        // The request timeout only covers the response headers; this deadline also bounds a stalled body,
        // so a hung download can never keep the generation job (and the shared scheduler thread) waiting.
        CompletableFuture<HttpResponse<Path>> download = http.sendAsync(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(1)).GET().build(), HttpResponse.BodyHandlers.ofFile(partial));
        try {
            HttpResponse<Path> response = download.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) throw new IOException("Model download returned " + response.statusCode());
            if (!sha256.equals(digest(partial))) throw new IOException("Model file digest mismatch");
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Model download interrupted", exception);
        } catch (TimeoutException exception) {
            throw new IOException("Model download exceeded its deadline", exception);
        } catch (ExecutionException exception) {
            throw new IOException("Model download failed", exception.getCause());
        } finally {
            download.cancel(true);
            // A cancelled transfer may still hold the file on Windows; the next attempt overwrites it.
            try { Files.deleteIfExists(partial); } catch (IOException ignored) { /* Retried on the next run. */ }
        }
    }

    static String digest(Path file) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            for (int read; (read = input.read(buffer)) > 0; ) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
