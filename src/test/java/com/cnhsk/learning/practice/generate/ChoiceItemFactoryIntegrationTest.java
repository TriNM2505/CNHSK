package com.cnhsk.learning.practice.generate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.text.TextNormalizer;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.ServedItemView;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** A dedicated PostgreSQL database keeps the "only three words" case independent of other suites. */
@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import({ChoiceItemFactory.class, ChoiceItemFactory.RandomConfiguration.class,
        ChoiceItemFactoryIntegrationTest.SqlConfiguration.class})
class ChoiceItemFactoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("choice_factory_test").withUsername("choice_factory_test")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired private ChoiceItemFactory factory;
    @Autowired private EntityManager entityManager;
    @Autowired private SqlCapture capture;
    @Autowired @Qualifier("practiceChoiceRandom") private RandomGenerator random;

    @BeforeEach
    void setup() {
        capture.statements.clear();
    }

    @Test
    void FR011_sameTopicCandidatesWinBeforeOutsideCandidates() {
        Lexeme target = word("我", "tôi", "wǒ", 1);
        List<Lexeme> sameTopic = List.of(word("你", "bạn", "nǐ", 1), word("他", "anh ấy", "tā", 1),
                word("人", "người", "rén", 1));
        long topicId = topic(target, sameTopic);
        word("朋友", "bạn bè", "péngyou", 1);
        word("老师", "giáo viên", "lǎoshī", 9);
        entityManager.flush();
        capture.statements.clear();
        var result = factory.create(target, topicId, ItemType.MEANING_CHOICE, 4).orElseThrow();
        assertThat(result.options()).extracting(ServedItem.Option::text).containsExactlyInAnyOrder("tôi", "bạn", "anh ấy", "người");
        assertThat(result.mixedDistractors()).isFalse();
        assertThat(candidateSelects()).allSatisfy(sql -> assertThat(sql).contains("topic_items").doesNotContain("hsk_level=?"));
    }

    @Test
    void FR011_AT21_smallTopicFallsBackToSameKindAndSameLevelAndMarksMixed() {
        Lexeme target = word("我", "tôi", "wǒ", 1);
        long topicId = topic(target, List.of(word("你", "bạn", "nǐ", 1), word("他", "anh ấy", "tā", 1)));
        for (int no = 0; no < 5; no++) {
            word("词" + (char) (0x4e00 + no), "ngoài chủ đề " + no, "cí", 1);
        }
        var result = factory.create(target, topicId, ItemType.MEANING_CHOICE, 4).orElseThrow();
        assertThat(result.options()).extracting(ServedItem.Option::text).contains("tôi", "bạn", "anh ấy");
        assertThat(result.options()).hasSize(4);
        assertThat(result.mixedDistractors()).isTrue();
    }

    @Test
    void FR011_AT21_onlyThreeWordsInIsolatedDatabaseCannotProduceFourOptions() {
        Lexeme target = word("我", "tôi", "wǒ", 1);
        word("你", "bạn", "nǐ", 1);
        word("他", "anh ấy", "tā", 1);
        assertThat(entityManager.createQuery("select count(l) from Lexeme l", Long.class).getSingleResult()).isEqualTo(3);
        assertThat(factory.create(target, null, ItemType.MEANING_CHOICE, 4)).isEmpty();
    }

    @Test
    void FR011_sameKindOtherLevelsAreUsedOnlyAfterSameLevelIncludingHskNine() {
        Lexeme target = word("我", "tôi", "wǒ", 2);
        long topicId = topic(target, List.of());
        word("你", "bạn", "nǐ", 2);
        word("他", "anh ấy", "tā", 1);
        word("人", "người", "rén", 9);
        Lexeme otherKind = word("字", "chữ", "zì", 2);
        otherKind.setKind(LexemeKind.CHAR);
        var result = factory.create(target, topicId, ItemType.MEANING_CHOICE, 4).orElseThrow();
        assertThat(result.options()).extracting(ServedItem.Option::text)
                .containsExactlyInAnyOrder("tôi", "bạn", "anh ấy", "người");
        assertThat(result.mixedDistractors()).isTrue();
    }

    @Test
    void FR011_databaseFiltersSameKindAndLevelBeforeMaterializingBoundedPages() {
        Lexeme target = word("我", "tôi", "wǒ", 2);
        word("你", "bạn", "nǐ", 2);
        word("他", "anh ấy", "tā", 2);
        word("人", "người", "rén", 2);
        word("老师", "giáo viên", "lǎoshī", 9);
        Lexeme otherKind = word("字", "chữ", "zì", 2);
        otherKind.setKind(LexemeKind.CHAR);
        entityManager.flush();
        capture.statements.clear();
        var result = factory.create(target, null, ItemType.MEANING_CHOICE, 4).orElseThrow();
        assertThat(result.options()).extracting(ServedItem.Option::text)
                .containsExactlyInAnyOrder("tôi", "bạn", "anh ấy", "người");
        assertThat(result.mixedDistractors()).isFalse();
        assertThat(candidateSelects()).isNotEmpty().allSatisfy(sql -> {
            assertThat(sql).containsPattern("(?i)(fetch first|limit)");
            String predicate = sql.substring(sql.indexOf(" where "));
            assertThat(predicate).contains("kind", "hsk_level").doesNotContain("extra");
        });
    }

    @Test
    void FR011_AT19_oneHundredDatabaseMeaningChoicesNeverDuplicateFatherMeaning() {
        Lexeme target = word("爸爸", "bố", "bàba", 1);
        long topicId = topic(target, List.of(word("父亲", "bố", "fùqīn", 1), word("妈妈", "mẹ", "māma", 1),
                word("朋友", "bạn", "péngyou", 1), word("老师", "giáo viên", "lǎoshī", 1)));
        for (int round = 0; round < 100; round++) {
            var result = factory.create(target, topicId, ItemType.MEANING_CHOICE, 4).orElseThrow();
            assertThat(result.options()).filteredOn(option -> option.text().equals("bố")).hasSize(1);
            assertThat(result.options()).filteredOn(option -> option.key().equals(result.correctAnswer()))
                    .extracting(ServedItem.Option::text).containsExactly("bố");
        }
    }

    @Test
    void FR011_AT20_oneHundredDatabaseListeningChoicesKeepToneAndRejectHomophones() {
        Lexeme target = word("是", "là", "shì", 1);
        long topicId = topic(target, List.of(word("事", "việc", "shì", 1), word("时", "thời gian", "shí", 1),
                word("人", "người", "rén", 1), word("我", "tôi", "wǒ", 1)));
        for (int round = 0; round < 100; round++) {
            var result = factory.create(target, topicId, ItemType.AUDIO_WORD_CHOICE, 4).orElseThrow();
            assertThat(result.options()).extracting(ServedItem.Option::text).contains("是", "时").doesNotContain("事");
        }
    }

    @Test
    void FR006_AT05_oneHundredCharacterAudioChoicesRejectSameToneHomophones() {
        Lexeme target = character("是", "là", "shì", "日");
        character("事", "việc", "shì", "日");
        character("时", "thời gian", "shí", "日");
        character("晴", "trời quang", "qíng", "日");
        character("明", "sáng", "míng", "日");

        for (int round = 0; round < 100; round++) {
            var result = factory.create(target, null, ItemType.AUDIO_CHAR_CHOICE, 4).orElseThrow();
            assertThat(result.options()).hasSize(4).extracting(ServedItem.Option::text)
                    .contains("是", "时", "晴", "明").doesNotContain("事");
            assertThat(result.options()).filteredOn(option -> option.key().equals(result.correctAnswer()))
                    .extracting(ServedItem.Option::text).containsExactly("是");
        }
    }

    @Test
    void FR011_factoryDefaultRandomBeanIsThreadLocalAndAnswersNeverEnterPublicItemView() {
        assertThat(random).isInstanceOf(ThreadLocalRandom.class);
        Lexeme target = word("我", "tôi", "wǒ", 1);
        word("你", "bạn", "nǐ", 1);
        word("他", "anh ấy", "tā", 1);
        word("人", "người", "rén", 1);
        var result = factory.create(target, null, ItemType.MEANING_CHOICE, 4).orElseThrow();
        var snapshot = new ServedItem(1, ItemType.MEANING_CHOICE, com.cnhsk.learning.practice.Skill.VOCAB,
                target.getId(), new ServedItem.Prompt(target.getText(), null, null, null, null, null, null, null, null),
                result.options(), result.correctAnswer(), null, null, new ServedItem.Rules(800, 0, false));
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.valueToTree(snapshot).path("answer").asText()).isEqualTo(result.correctAnswer());
        var view = mapper.valueToTree(ServedItemView.from(snapshot));
        assertThat(view.has("answer")).isFalse();
        assertThat(view.has("accept")).isFalse();
        assertThat(view.has("correctAnswer")).isFalse();
        assertThat(mapper.valueToTree(result).has("correctAnswer")).isFalse();
    }

    private Lexeme word(String text, String meaning, String pinyin, int level) {
        Lexeme word = new Lexeme(LexemeKind.WORD, text, meaning);
        word.setMeaningNorm(TextNormalizer.normalizeMeaning(meaning));
        word.setPinyin(pinyin);
        word.setHskLevel((short) level);
        entityManager.persist(word);
        return word;
    }

    private Lexeme character(String text, String meaning, String pinyin, String radical) {
        Lexeme character = new Lexeme(LexemeKind.CHAR, text, meaning);
        character.setMeaningNorm(TextNormalizer.normalizeMeaning(meaning));
        character.setPinyin(pinyin);
        character.setHskLevel((short) 1);
        character.setRadical(radical);
        entityManager.persist(character);
        return character;
    }

    private long topic(Lexeme target, List<Lexeme> words) {
        Topic topic = new Topic("TEST_" + UUID.randomUUID().toString().replace("-", ""), "Chủ đề kiểm thử", (short) 1);
        entityManager.persist(topic);
        entityManager.persist(new TopicItem(topic.getId(), target.getId()));
        words.forEach(word -> entityManager.persist(new TopicItem(topic.getId(), word.getId())));
        return topic.getId();
    }

    private List<String> candidateSelects() {
        return capture.statements.stream().map(sql -> sql.toLowerCase(Locale.ROOT))
                .filter(sql -> sql.contains("from learning.lexemes") && !sql.contains("count("))
                .toList();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean
        SqlCapture sqlCapture() {
            return new SqlCapture();
        }

        @Bean
        HibernatePropertiesCustomizer choiceSqlInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class SqlCapture implements StatementInspector {
        final List<String> statements = new ArrayList<>();

        @Override
        public String inspect(String sql) {
            statements.add(sql);
            return sql;
        }
    }
}
