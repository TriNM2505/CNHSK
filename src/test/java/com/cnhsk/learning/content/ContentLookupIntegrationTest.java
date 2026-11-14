package com.cnhsk.learning.content;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.learning.api.ContentLookup;
import com.cnhsk.learning.api.ContentLookup.GameContentQuery;
import com.cnhsk.learning.api.ContentLookup.LexemeCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Transactional
@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import(ContentLookupImpl.class)
class ContentLookupIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-14T01:00:00Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    // Pool cardinality must not depend on committed fixtures in other integration suites.
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("content_lookup_test").withUsername("content_lookup_test")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired private ContentLookup lookup;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate named;
    private long learner;
    private long other;

    @BeforeEach
    void fixture() {
        learner = user(); other = user();
    }

    @Test
    void FR022_AT17_newLearnerAndGuestDefaultToLevelOneCharactersAndWords() {
        long character = lexeme("CHAR", 1);
        long word = lexeme("WORD", 1);
        lexeme("WORD", 2); lexeme("CHAR", 3); lexeme("GRAMMAR", 1); lexeme("WORD", null);
        for (Long user : new Long[]{null, learner}) {
            var cards = lookup.pool(query(null, null, user, 200));
            assertThat(ids(cards)).containsExactlyInAnyOrder(character, word);
            assertThat(cards).allSatisfy(card -> assertThat(card.hskLevel()).isEqualTo(1));
        }
    }

    @Test
    void FR022_AT17_prioritizesAllFifteenLearnedWordsThenFillsFromLevelOneWithoutDuplicates() {
        Set<Long> learned = new HashSet<>();
        for (int index = 0; index < 15; index++) {
            long id = lexeme("WORD", index % 2 == 0 ? 2 : 3);
            studied(learner, id, "TOPIC_STUDY", "STUDY_CARD", "IN_PROGRESS");
            learned.add(id);
        }
        studied(learner, learned.iterator().next(), "TOPIC_STUDY", "STUDY_CARD", "ABANDONED");
        Set<Long> fallback = new HashSet<>();
        for (int index = 0; index < 10; index++) fallback.add(lexeme("WORD", 1));
        long foreign = lexeme("WORD", 3);
        studied(other, foreign, "TOPIC_STUDY", "STUDY_CARD", "SUBMITTED");
        long wrongSource = lexeme("WORD", 3);
        studied(learner, wrongSource, "TOPIC_PRACTICE", "STUDY_CARD", "SUBMITTED");
        long wrongItem = lexeme("WORD", 3);
        studied(learner, wrongItem, "TOPIC_STUDY", "MEANING_CHOICE", "SUBMITTED");
        for (int repeat = 0; repeat < 4; repeat++) {
            var cards = lookup.pool(query(Set.of("WORD"), null, learner, 20));
            assertThat(cards).hasSize(20);
            assertThat(ids(cards.subList(0, 15))).containsExactlyInAnyOrderElementsOf(learned);
            assertThat(fallback).containsAll(ids(cards.subList(15, 20)));
            assertThat(ids(cards)).doesNotHaveDuplicates().doesNotContain(foreign, wrongSource, wrongItem);
        }
        assertThat(learned).containsAll(ids(lookup.pool(query(Set.of("WORD"), null, learner, 10))));
    }

    @Test
    void FR020_FR022_selectedLevelsApplyToBothLearnedAndFallbackGroups() {
        long learnedOne = lexeme("WORD", 1);
        long learnedTwo = lexeme("WORD", 2);
        long learnedThree = lexeme("WORD", 3);
        for (long id : new long[]{learnedOne, learnedTwo, learnedThree}) {
            studied(learner, id, "TOPIC_STUDY", "STUDY_CARD", "SUBMITTED");
        }
        long freshTwo = lexeme("WORD", 2);
        long freshThree = lexeme("WORD", 3);
        var cards = lookup.pool(query(Set.of("WORD"), Set.of(2), learner, 200));
        assertThat(ids(cards)).containsExactly(learnedTwo, freshTwo);
        assertThat(cards).allSatisfy(card -> assertThat(card.hskLevel()).isEqualTo(2));
        assertThat(ids(lookup.pool(query(null, Set.of(1, 3), learner, 200))))
                .containsExactlyInAnyOrder(learnedOne, learnedThree, freshThree);
        assertThat(lookup.pool(query(null, Set.of(9), learner, 200))).isEmpty();
    }

    @Test
    void FR020_combinedRequirementsExcludeNullUnicodeBlankAndNonpositiveCountsEvenForLearnedCards() {
        long valid = media("CHAR", "https://example.test/voice.mp3", "木", 4);
        List<Long> invalid = new ArrayList<>();
        for (String missing : new String[]{null, "", " \t\r\n", "\u00A0", "\u3000"}) {
            invalid.add(media("CHAR", missing, "木", 4));
            invalid.add(media("CHAR", "https://example.test/voice.mp3", missing, 4));
        }
        for (Integer count : new Integer[]{null, 0, -1}) invalid.add(media("CHAR", "https://example.test/voice.mp3", "木", count));
        for (long id : invalid) studied(learner, id, "TOPIC_STUDY", "STUDY_CARD", "SUBMITTED");
        var required = new GameContentQuery(Set.of("CHAR"), null, true, true, true, learner, 200);
        assertThat(ids(lookup.pool(required))).containsExactly(valid);
        assertThat(lookup.pool(query(Set.of("CHAR"), null, learner, 200))).hasSize(invalid.size() + 1);
    }

    @Test
    void FR020_eachRequirementAndKindFilterOperateIndependently() {
        long all = media("CHAR", "https://example.test/a.mp3", "木", 4);
        long audio = media("CHAR", "https://example.test/a.mp3", null, null);
        long radical = media("CHAR", null, "木", null);
        long strokes = media("CHAR", null, null, 4);
        long word = media("WORD", "https://example.test/a.mp3", "木", 4);
        assertThat(ids(lookup.pool(new GameContentQuery(Set.of("CHAR"), null, true, false, false, null, 200))))
                .containsExactlyInAnyOrder(all, audio);
        assertThat(ids(lookup.pool(new GameContentQuery(Set.of("CHAR"), null, false, true, false, null, 200))))
                .containsExactlyInAnyOrder(all, radical);
        assertThat(ids(lookup.pool(new GameContentQuery(Set.of("CHAR"), null, false, false, true, null, 200))))
                .containsExactlyInAnyOrder(all, strokes);
        assertThat(ids(lookup.pool(query(Set.of("WORD"), null, null, 200)))).containsExactly(word);
    }

    @Test
    void FR021_realProjectionPreservesElevenFieldsAndDoesNotRewriteBadOrMissingPinyin() {
        long id = lexeme("WORD", 1);
        jdbc.update("UPDATE learning.lexemes SET text=?,pinyin=?,meaning_vi=?,meaning_norm=?,audio_url=?,radical=?,stroke_count=? WHERE id=?",
                "女孩", "nǚ hái", "cô gái; thiếu nữ", "co gai;thieu nu,co gai", "https://example.test/a.mp3", "女", 6, id);
        var cards = lookup.pool(query(null, null, null, 200));
        assertThat(cards).containsExactly(new LexemeCard(id, "WORD", "女孩", "nǚ hái", "nv3hai2", "cô gái",
                Set.of("co gai", "thieu nu"), "女", 6, "https://example.test/a.mp3", 1));
        assertThatThrownBy(() -> cards.clear()).isInstanceOf(UnsupportedOperationException.class);
        jdbc.update("UPDATE learning.lexemes SET pinyin=?,meaning_norm=null WHERE id=?", "invalid-pinyin-123", id);
        var bad = lookup.pool(query(null, null, null, 200)).get(0);
        assertThat(bad.pinyinKey()).isNull();
        assertThat(bad.meaningKeys()).containsExactlyInAnyOrder("co gai", "thieu nu");
        assertThat(jdbc.queryForObject("SELECT pinyin FROM learning.lexemes WHERE id=?", String.class, id)).isEqualTo("invalid-pinyin-123");
        jdbc.update("UPDATE learning.lexemes SET pinyin=null WHERE id=?", id);
        assertThat(lookup.pool(query(null, null, null, 200)).get(0).pinyinKey()).isNull();
    }

    @Test
    void FR020_oneQueryAndDatabaseLimitStayBoundedWhenTheBankGrowsBeyondTwoHundred() {
        var counted = spy(named);
        var impl = new ContentLookupImpl(counted);
        lexeme("WORD", 1);
        assertThat(impl.pool(query(null, null, null, 200))).hasSize(1);
        verify(counted, times(1)).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        clearInvocations(counted);
        for (int index = 0; index < 220; index++) lexeme("WORD", 1);
        var cards = impl.pool(query(null, null, null, 200));
        assertThat(cards).hasSize(200);
        assertThat(ids(cards)).doesNotHaveDuplicates();
        verify(counted, times(1)).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        assertThat(impl.pool(query(null, null, null, 1))).hasSize(1);
    }

    private GameContentQuery query(Set<String> kinds, Set<Integer> levels, Long userId, int limit) {
        return new GameContentQuery(kinds, levels, false, false, false, userId, limit);
    }

    private List<Long> ids(List<LexemeCard> cards) { return cards.stream().map(LexemeCard::id).toList(); }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Lookup fixture");
    }

    private long lexeme(String kind, Integer level) {
        return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi,meaning_norm,hsk_level) "
                        + "VALUES (?,?,?,'nghĩa tự soạn','nghia tu soan',?) RETURNING id", Long.class,
                kind, "自编-" + UUID.randomUUID(), "zì", level);
    }

    private long media(String kind, String audio, String radical, Integer count) {
        long id = lexeme(kind, 1);
        jdbc.update("UPDATE learning.lexemes SET audio_url=?,radical=?,stroke_count=? WHERE id=?", audio, radical, count, id);
        return id;
    }

    private void studied(long userId, long lexemeId, String source, String itemType, String status) {
        long attempt = jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind,ref_type,status,served_at,submitted_at) "
                        + "VALUES (?,'PRACTICE',?,?,?,?) RETURNING id", Long.class, userId, source, status,
                Timestamp.from(NOW), status.equals("SUBMITTED") ? Timestamp.from(NOW.plusSeconds(10)) : null);
        jdbc.update("INSERT INTO learning.attempt_items(attempt_id,lexeme_id,item_type,skill,order_index,answer) "
                + "VALUES (?,?,?,'VOCAB',1,'LEARNED')", attempt, lexemeId, itemType);
    }
}
