package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.cnhsk.learning.library.Segmenter.Result;
import com.cnhsk.learning.library.Segmenter.Segment;
import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Fixtures commit before each measured read; the tests have no enclosing rollback transaction. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SegmenterIntegrationTest extends AbstractIntegrationTest {
    @Autowired private Segmenter segmenter;
    @Autowired private SegmenterQueryRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory factory;
    @Autowired private PlatformTransactionManager transactions;
    private final List<Long> fixtureIds = new ArrayList<>();
    private String longText;
    private String allKinds;
    private String grammarAndChar;
    private String onlyChar;
    private String nullPinyin;
    private long wordId;
    private long grammarId;
    private long charId;
    private long nullPinyinId;
    private int fixtureStart;

    @BeforeAll
    void committedFixtures() {
        fixtureStart = 0x22000 + Math.floorMod(UUID.randomUUID().hashCode(), 4096);
        longText = new String(java.util.stream.IntStream.range(fixtureStart, fixtureStart + 2000).toArray(), 0, 2000);
        new TransactionTemplate(transactions).execute(status -> {
            fixtureIds.addAll(jdbc.queryForList("""
                    INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi)
                    SELECT 'CHAR',chr(?+n),'p','Mục tự soạn cho benchmark' FROM generate_series(0,1999) n
                    ON CONFLICT (kind,text) DO NOTHING RETURNING id
                    """, Long.class, fixtureStart));
            allKinds = unusedText();
            long lowId = insert("CHAR", allKinds, "char-fixture");
            insert("GRAMMAR", allKinds, "grammar-fixture");
            wordId = insert("WORD", allKinds, "word-fixture");
            assertThat(wordId).isGreaterThan(lowId);
            grammarAndChar = unusedText();
            insert("CHAR", grammarAndChar, "char-fixture");
            grammarId = insert("GRAMMAR", grammarAndChar, "grammar-fixture");
            onlyChar = unusedText();
            charId = insert("CHAR", onlyChar, "char-fixture");
            nullPinyin = unusedText();
            insert("CHAR", nullPinyin, "char-with-pinyin");
            nullPinyinId = insert("WORD", nullPinyin, null);
            return null;
        });
    }

    @Test
    @Order(1)
    void AT20_2000CodePointsUseOneArrayQueryAndReportFirstP50P95() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(longText.codePointCount(0, longText.length())).isEqualTo(2000);
        assertThat(longText).hasSize(4000);
        Statistics statistics = factory.unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            long start = System.nanoTime();
            Result first = segmenter.segment(longText);
            double firstMs = (System.nanoTime() - start) / 1_000_000.0;
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            assertThat(Segmenter.candidates(longText)).hasSize(15_972);
            assertThat(first.segments().stream().map(Segment::text).collect(Collectors.joining())).isEqualTo(longText);
            assertThat(first.segments()).allSatisfy(segment -> assertThat(segment.lexemeId()).isNotNull());
            assertThat(first.pinyin()).isNotNull();
            for (int warmup = 0; warmup < 3; warmup++) assertStatements(1, () -> segmenter.segment(longText));
            double[] samples = new double[20];
            for (int sample = 0; sample < samples.length; sample++) {
                statistics.clear();
                start = System.nanoTime();
                segmenter.segment(longText);
                samples[sample] = (System.nanoTime() - start) / 1_000_000.0;
                assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            }
            Arrays.sort(samples);
            System.out.printf(Locale.ROOT,
                    "SEGMENTER_BENCHMARK code_points=2000 candidates=15972 selects=1 first_ms=%.3f p50_ms=%.3f p95_ms=%.3f samples=20%n",
                    firstMs, samples[9], samples[18]);
        } finally { statistics.setStatisticsEnabled(previous); }
    }

    @Test
    void Q_T13_01_wordThenGrammarThenCharEvenWhenLowerPriorityHasSmallerId() {
        assertThat(assertStatements(1, () -> segmenter.segment(allKinds)).segments())
                .containsExactly(new Segment(allKinds, "word-fixture", wordId));
        assertThat(assertStatements(1, () -> segmenter.segment(grammarAndChar)).segments())
                .containsExactly(new Segment(grammarAndChar, "grammar-fixture", grammarId));
        assertThat(assertStatements(1, () -> segmenter.segment(onlyChar)).segments())
                .containsExactly(new Segment(onlyChar, "char-fixture", charId));
    }

    @Test
    void nullPinyinIsReturnedAsNullAndSkippedWithoutChoosingLowerPriorityKind() {
        Result result = assertStatements(1, () -> segmenter.segment(nullPinyin));
        assertThat(result.segments()).containsExactly(new Segment(nullPinyin, null, nullPinyinId));
        assertThat(result.pinyin()).isNull();
        Result mixed = assertStatements(1, () -> segmenter.segment(nullPinyin + " HSK 3，。 " + allKinds));
        assertThat(mixed.pinyin()).isEqualTo("word-fixture");
        assertThat(mixed.segments().get(1)).isEqualTo(new Segment(" HSK 3，。 ", null, null));
    }

    @Test
    void noCjkAndEmptyInputUseZeroSelects() {
        assertThat(assertStatements(0, () -> segmenter.segment("")).segments()).isEmpty();
        String text = " 2026 HSK 3，。\n🙂 ";
        assertThat(assertStatements(0, () -> segmenter.segment(text)).segments()).containsExactly(new Segment(text, null, null));
        assertThat(assertStatements(0, () -> repository.lookup(Set.of()))).isEmpty();
    }

    @Test
    void repositoryBindsOneTypedArrayAndReturnsOnlyMatchingRows() {
        var result = assertStatements(1, () -> new TransactionTemplate(transactions).execute(status ->
                repository.lookup(Set.of(allKinds, grammarAndChar, onlyChar, nullPinyin))));
        assertThat(result).hasSize(4);
        assertThat(result.get(allKinds).id()).isEqualTo(wordId);
        assertThat(result.get(nullPinyin).pinyin()).isNull();
    }

    private <T> T assertStatements(long expected, Supplier<T> call) {
        Statistics statistics = factory.unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            T result = call.get();
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(expected);
            return result;
        } finally { statistics.setStatisticsEnabled(previous); }
    }

    private String unusedText() {
        int point = jdbc.queryForObject("""
                SELECT n FROM generate_series(?,?) n
                WHERE NOT EXISTS (SELECT 1 FROM learning.lexemes WHERE text=chr(n)) ORDER BY n LIMIT 1
                """, Integer.class, fixtureStart + 3000, fixtureStart + 4000);
        return new String(Character.toChars(point));
    }

    private long insert(String kind, String text, String pinyin) {
        long id = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi) VALUES (?,?,?,'Mục tự soạn') RETURNING id", Long.class, kind, text, pinyin);
        fixtureIds.add(id);
        return id;
    }

    @AfterAll
    void removeOnlyCommittedFixtureRows() {
        if (fixtureIds.isEmpty()) return;
        new TransactionTemplate(transactions).execute(status -> {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                java.sql.Array ids = connection.createArrayOf("bigint", fixtureIds.toArray(Long[]::new));
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM learning.lexemes WHERE id=ANY(?)")) {
                    statement.setArray(1, ids);
                    statement.executeUpdate();
                } finally { ids.free(); }
                return null;
            });
            return null;
        });
    }
}
