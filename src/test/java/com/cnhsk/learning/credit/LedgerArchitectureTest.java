package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class LedgerArchitectureTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Pattern MUTATION = Pattern.compile(
            "(?i)\\b(?:UPDATE|DELETE\\s+FROM|TRUNCATE(?:\\s+TABLE)?|MERGE\\s+INTO)\\s+"
                    + "(?:learning\\s*\\.\\s*)?credit_transactions\\b");
    private static final Pattern INSERT = Pattern.compile(
            "(?i)\\bINSERT\\s+INTO\\s+(?:learning\\s*\\.\\s*)?credit_transactions\\b");

    @Test
    void productionLedgerHasNoUpdateDeleteOrMutableJpaMapping() throws IOException {
        for (Path file : productionFiles()) {
            String source = normalizedSource(file);
            assertThat(MUTATION.matcher(source).find()).as("Ledger mutation in %s", file).isFalse();
            assertThat(Pattern.compile("(?is)@Table\\s*\\([^)]*\\bname\\s*=\\s*credit_transactions\\b")
                    .matcher(source).find()).as("Mutable ledger entity in %s", file).isFalse();
        }
    }

    @Test
    void ledgerServiceIsTheOnlyInsertWriter() throws IOException {
        List<Path> writers = new ArrayList<>();
        for (Path file : productionFiles()) {
            if (INSERT.matcher(normalizedSource(file)).find()) {
                writers.add(file);
            }
        }
        assertThat(writers).containsExactly(MAIN.resolve(Path.of("com", "cnhsk", "learning", "credit", "LedgerService.java")));
    }

    @Test
    void appendMustJoinTheCallerTransaction() throws NoSuchMethodException {
        Transactional boundary = LedgerService.class.getMethod("append", LedgerService.AppendCommand.class)
                .getAnnotation(Transactional.class);
        assertThat(boundary).isNotNull();
        assertThat(boundary.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    private static List<Path> productionFiles() throws IOException {
        try (var files = Files.walk(MAIN)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String normalizedSource(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\"", "").replaceAll("\\s+", " ");
    }
}
