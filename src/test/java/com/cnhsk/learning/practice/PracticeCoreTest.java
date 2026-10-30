package com.cnhsk.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class PracticeCoreTest {

    @ParameterizedTest
    @CsvSource(value = {
            "TOPIC_STUDY, PRACTICE, RECORD, 7200, true, false",
            "TOPIC_PRACTICE, PRACTICE, IMMEDIATE, 7200, true, false",
            "TOPIC_TEST, TOPIC_TEST, DEFERRED, 86400, true, false",
            "GRAMMAR_LESSON, PRACTICE, IMMEDIATE, 7200, true, false",
            "PRON_STAGE, PRACTICE, IMMEDIATE, 604800, false, false",
            "CHAR_RECOGNITION, PRACTICE, IMMEDIATE, 7200, true, false",
            "CHAR_WRITING, PRACTICE, RECORD, 1800, true, false",
            "WRITING_CHALLENGE, WRITING_CHALLENGE, DEFERRED, 210, true, false",
            "GAME_ROUND, GAME, IMMEDIATE, DYNAMIC, false, true"
    }, nullValues = "DYNAMIC")
    void FR001_nineSourcesKeepSpecifiedLifetimeFeedbackAndScoringPolicy(
            PracticeSource source, AttemptKind kind, FeedbackMode feedback, Long ttlSeconds,
            boolean allowSkip, boolean maxScoreFromServed) {
        assertThat(source.kind()).isEqualTo(kind);
        assertThat(source.feedback()).isEqualTo(feedback);
        assertThat(source.allowSkip()).isEqualTo(allowSkip);
        assertThat(source.maxScoreFromServed()).isEqualTo(maxScoreFromServed);
        Duration expectedTtl = ttlSeconds == null ? null : Duration.ofSeconds(ttlSeconds);
        assertThat(source.ttl()).isEqualTo(expectedTtl);
    }

    @Test
    void FR001_coreEnumsMatchTheApprovedOpenApi() {
        OpenAPI contract = new OpenAPIV3Parser().read(
                "specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml");
        assertThat(contract).isNotNull();
        assertContractEnum(contract, "PracticeSource", PracticeSource.values());
        assertContractEnum(contract, "FeedbackMode", FeedbackMode.values());
        assertContractEnum(contract, "Skill", Skill.values());
    }

    @ParameterizedTest
    @EnumSource(ItemType.class)
    void FR007_studyCardsCannotBeGradedWhileQuestionTypesCan(ItemType type) {
        assertThat(type.gradable()).isEqualTo(!Set.of(ItemType.STUDY_CARD, ItemType.WRITING_RESULT).contains(type));
    }

    @Test
    void FR007_defaultSourceHooksAcceptRecordEvidenceWithoutAddingMetadata() {
        PracticeSourceHandler handler = new PracticeSourceHandler() {
            @Override
            public PracticeSource source() {
                return PracticeSource.TOPIC_STUDY;
            }

            @Override
            public Map<String, Object> onFinish(FinishContext context) {
                return Map.of();
            }
        };
        PracticeSourceHandler.Answer answer = new PracticeSourceHandler.Answer(
                1, "LEARNED", 1000, 0, false, false, null);
        ServedItem item = studyCard();

        assertThatCode(() -> handler.validateAnswer(item, answer)).doesNotThrowAnyException();
        assertThat(handler.enrichMeta(item, answer)).isEmpty();
    }

    @Test
    void FR007_sourceContextCopiesEvidenceAndKeepsJsonNullValuesForSourceValidation() {
        Map<String, Object> clientMeta = new LinkedHashMap<>();
        clientMeta.put("accuracy", null);
        PracticeSourceHandler.Answer answer = new PracticeSourceHandler.Answer(
                1, "KNOWN", 200, 0, false, false, clientMeta);
        Map<String, Object> meta = new LinkedHashMap<>(Map.of("selfDeclared", true));
        PracticeSourceHandler.GradedItem graded = new PracticeSourceHandler.GradedItem(
                studyCard(), answer, null, false, meta);
        List<PracticeSourceHandler.GradedItem> items = new ArrayList<>(List.of(graded));
        Instant servedAt = Instant.parse("2026-10-30T23:30:00Z");
        PracticeSourceHandler.FinishContext context = new PracticeSourceHandler.FinishContext(
                10L, 20L, PracticeSource.TOPIC_STUDY, 30L, null, null, false,
                servedAt, servedAt.plusSeconds(3), items);

        clientMeta.put("accuracy", 100);
        meta.put("selfDeclared", false);
        items.clear();

        assertThat(answer.clientMeta()).containsEntry("accuracy", null);
        assertThat(context.items()).containsExactly(graded);
        assertThat(graded.meta()).containsEntry("selfDeclared", true);
        assertThat(graded.correct()).isNull();
        assertThat(graded.counted()).isFalse();
        assertThatThrownBy(() -> answer.clientMeta().put("accuracy", 100))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> graded.meta().put("selfDeclared", false))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> context.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void FR007_absentClientEvidenceAndGradingMetadataRemainEmpty() {
        PracticeSourceHandler.Answer answer = new PracticeSourceHandler.Answer(
                1, "LEARNED", 1000, 0, false, false, null);
        PracticeSourceHandler.GradedItem graded = new PracticeSourceHandler.GradedItem(
                studyCard(), answer, null, false, null);

        assertThat(answer.clientMeta()).isEmpty();
        assertThat(graded.meta()).isEmpty();
    }

    private static void assertContractEnum(OpenAPI contract, String name, Enum<?>[] values) {
        List<?> declaredValues = contract.getComponents().getSchemas().get(name).getEnum();
        assertThat(declaredValues).isNotNull();
        assertThat(declaredValues.stream().map(String::valueOf).toList())
                .containsExactlyElementsOf(Arrays.stream(values).map(Enum::name).toList());
    }

    private static ServedItem studyCard() {
        ServedItem.Prompt prompt = new ServedItem.Prompt(
                "我", "wǒ", null, "tôi", null, null, null, null, "CHAR");
        return new ServedItem(1, ItemType.STUDY_CARD, Skill.VOCAB, 1L, prompt,
                null, null, null, null, new ServedItem.Rules(1000, 0, false));
    }
}
