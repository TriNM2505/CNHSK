package com.cnhsk.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.practice.web.PracticeController;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.cnhsk.shared.security.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.persistence.EntityManager;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PracticeServiceTest {

    static final Instant NOW = Instant.parse("2026-10-30T23:30:00Z");
    private final AttemptRepository attempts = mock(AttemptRepository.class);
    private final AttemptItemRepository items = mock(AttemptItemRepository.class);
    private final PracticeRateLimiter limiter = mock(PracticeRateLimiter.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final List<AttemptItem> recorded = new ArrayList<>();
    private PracticeService service;
    private Attempt saved;

    @BeforeEach
    void setup() {
        service = service(List.of());
        when(attempts.saveAndFlush(any())).thenAnswer(invocation -> {
            saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });
        when(items.findByAttemptIdOrderByOrderIndexAsc(anyLong())).thenReturn(recorded);
        when(items.save(any())).thenAnswer(invocation -> {
            recorded.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }

    @ParameterizedTest
    @EnumSource(PracticeSource.class)
    void FR001_startUsesSourceKindAndServerClockForEverySource(PracticeSource source) {
        Duration override = source == PracticeSource.GAME_ROUND ? Duration.ofSeconds(45) : null;
        var view = service.start(command(source, List.of(choice(1)), List.of(), override, Map.of("roundSeconds", 30)));
        assertThat(view.sessionId()).isEqualTo(42L);
        assertThat(saved.getKind()).isEqualTo(source.kind().name());
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getRefId()).isEqualTo(9L);
        assertThat(view.source()).isEqualTo(source);
        assertThat(view.feedback()).isEqualTo(source.feedback());
        assertThat(view.servedAt()).isEqualTo(NOW);
        assertThat(view.expiresAt()).isEqualTo(NOW.plus(override == null ? source.ttl() : override));
        assertThat(view.status()).isEqualTo("IN_PROGRESS");
        assertThat(view.meta()).containsEntry("roundSeconds", 30);
        assertThat(saved.getGameCode()).isEqualTo(source == PracticeSource.GAME_ROUND ? "test-game" : null);
        verify(limiter).checkStart(7L);
    }

    @Test
    void FR002_serverSnapshotKeepsAnswersButTheSerializedViewDoesNot() throws Exception {
        var view = service.start(command(PracticeSource.TOPIC_PRACTICE, List.of(choice(1)), List.of(), null, Map.of()));
        assertThat(saved.getServedItems().path("items").get(0).path("answer").asText()).isEqualTo("A");
        assertThat(saved.getServedItems().path("items").get(0).path("accept").get(0).asText()).isEqualTo("secret-pinyin");
        String json = mapper.writeValueAsString(view);
        assertThat(json).doesNotContain("\"answer\"", "\"accept\"", "secret-pinyin", "secret-explanation");
        assertThat(view.items().get(0).no()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void FR001_gameRejectsNonPositiveLifetime(long seconds) {
        assertThatThrownBy(() -> service.start(command(PracticeSource.GAME_ROUND, List.of(choice(1)), List.of(),
                Duration.ofSeconds(seconds), Map.of()))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(attempts, limiter);
    }

    @Test
    void FR001_gameRequiresAnExplicitLifetime() {
        assertThatThrownBy(() -> service.start(command(PracticeSource.GAME_ROUND, List.of(choice(1)), List.of(),
                null, Map.of()))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(attempts, limiter);
    }

    @ParameterizedTest
    @EnumSource(value = PracticeSource.class, mode = EnumSource.Mode.EXCLUDE, names = "GAME_ROUND")
    void FR001_fixedLifetimeRejectsEvenAnIdenticalOverride(PracticeSource source) {
        assertThatThrownBy(() -> service.start(command(source, List.of(choice(1)), List.of(), source.ttl(), Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(attempts, limiter);
    }

    @Test
    void FR002_preRecordedEvidenceRunsSourceHooksAndPreservesTheLearnersAnswer() throws Exception {
        PracticeSourceHandler handler = mock(PracticeSourceHandler.class);
        when(handler.source()).thenReturn(PracticeSource.TOPIC_STUDY);
        when(handler.enrichMeta(any(), any())).thenReturn(Map.of("selfDeclared", true));
        service = service(List.of(handler));
        var answer = new Answer(1, "KNOWN", 100, 0, false, false, Map.of("untrusted", true));
        var view = service.start(command(PracticeSource.TOPIC_STUDY, List.of(card(1)), List.of(answer), null, Map.of()));
        verify(handler).validateAnswer(card(1), answer);
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getCorrect()).isNull();
        assertThat(recorded.get(0).getAnswer()).isEqualTo("KNOWN");
        assertThat(recorded.get(0).getDurationMs()).isEqualTo(100);
        assertThat(recorded.get(0).getMeta()).containsEntry("selfDeclared", true).containsEntry("suspicious", true)
                .doesNotContainKey("untrusted");
        assertThat(view.answered().get(0).answer()).isEqualTo("KNOWN");
        assertThat(view.answered().get(0).counted()).isFalse();
        assertThat(mapper.writeValueAsString(view)).doesNotContain("correctAnswer", "accept", "explanation");
    }

    @Test
    void FR002_preRecordedSkipAndAudioFailureAreEvidenceWithoutScores() {
        var audioCard = new ServedItem(1, ItemType.STUDY_CARD, Skill.VOCAB, null, card(1).prompt(), null,
                null, null, null, new ServedItem.Rules(0, 3, true));
        var view = service.start(command(PracticeSource.TOPIC_STUDY, List.of(audioCard),
                List.of(new Answer(1, null, 1000, 1, true, true, null)), null, Map.of()));
        assertThat(recorded.get(0).getMeta()).containsEntry("skipped", true).containsEntry("audioFailed", true)
                .containsEntry("replayCount", 1).doesNotContainKey("suspicious");
        assertThat(view.answered().get(0).correct()).isNull();
    }

    @Test
    void FR002_rejectsDuplicateNonPositiveAndUnservedItemNumbers() {
        for (List<ServedItem> served : List.of(List.of(card(0)), List.of(card(1), card(1)))) {
            assertThatThrownBy(() -> service.start(command(PracticeSource.TOPIC_STUDY, served, List.of(), null, Map.of())))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.start(command(PracticeSource.TOPIC_STUDY, List.of(card(1)),
                List.of(new Answer(2, "KNOWN", 1000, 0, false, false, null)), null, Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(attempts, limiter);
    }

    @Test
    void FR002_preRecordedRejectsGradableOrDuplicateEvidence() {
        Answer answer = new Answer(1, "KNOWN", 1000, 0, false, false, null);
        assertThatThrownBy(() -> service.start(command(PracticeSource.TOPIC_STUDY, List.of(choice(1)),
                List.of(answer), null, Map.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start(command(PracticeSource.TOPIC_STUDY, List.of(card(1)),
                List.of(answer, answer), null, Map.of()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void FR002_preRecordedRejectsInvalidDurationAnswerAndAudioEvidence() {
        List<Answer> invalid = List.of(
                new Answer(1, "KNOWN", -1, 0, false, false, null),
                new Answer(1, "KNOWN", 3_600_001, 0, false, false, null),
                new Answer(1, "KNOWN", 1000, -1, false, false, null),
                new Answer(1, null, 1000, 0, false, false, null),
                new Answer(1, "x".repeat(101), 1000, 0, false, false, null),
                new Answer(1, "KNOWN", 1000, 1, false, false, null),
                new Answer(1, null, 1000, 0, false, true, null));
        for (Answer answer : invalid) {
            assertThatThrownBy(() -> service.start(command(PracticeSource.TOPIC_STUDY, List.of(card(1)),
                    List.of(answer), null, Map.of()))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.start(command(PracticeSource.PRON_STAGE, List.of(card(1)),
                List.of(new Answer(1, null, 1000, 0, true, false, null)), null, Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void FR002_metadataRejectsSecretFieldsAtAnyDepthBeforeWriting() {
        for (String key : List.of("answer", "accept", "correctAnswer", "explanation")) {
            assertThatThrownBy(() -> service.start(command(PracticeSource.TOPIC_STUDY, List.of(card(1)), List.of(),
                    null, Map.of("nested", List.of(Map.of(key, "secret"))))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(attempts, limiter);
    }

    @Test
    void FR002_snapshotIsIndependentOfSubsequentMetadataMutation() {
        var nested = new ArrayList<>(List.of("original"));
        service.start(command(PracticeSource.TOPIC_STUDY, List.of(card(1)), List.of(), null, Map.of("labels", nested)));
        nested.add("changed");
        assertThat(saved.getServedItems().path("meta").path("labels")).hasSize(1);
    }

    @Test
    void FR010_missingAndUnownedViewsHaveIdenticalStatusCodeAndMessage() {
        when(attempts.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        ApiException missing = assertThrows(ApiException.class, () -> service.view(7, 99));
        service.start(command(PracticeSource.TOPIC_STUDY, List.of(card(1)), List.of(), null, null));
        when(attempts.findByIdForUpdate(42L)).thenReturn(Optional.of(saved));
        ApiException unowned = assertThrows(ApiException.class, () -> service.view(8, 42));
        assertThat(missing.getCode()).isEqualTo(ErrorCode.ATTEMPT_NOT_OWNED).isEqualTo(unowned.getCode());
        assertThat(missing.getStatus()).isEqualTo(HttpStatus.FORBIDDEN).isEqualTo(unowned.getStatus());
        assertThat(missing.getMessage()).isEqualTo(unowned.getMessage());
        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void FR010_expiryChangesOnlyAnOwnedInProgressSessionAndRetainsEvidence() {
        service.start(command(PracticeSource.TOPIC_STUDY, List.of(card(1)),
                List.of(new Answer(1, "KNOWN", 1200, 0, false, false, null)), null, null));
        when(attempts.findByIdForUpdate(42L)).thenReturn(Optional.of(saved));
        saved.setExpiresAt(NOW.minusNanos(1));
        var view = service.view(7, 42);
        assertThat(view.status()).isEqualTo("EXPIRED");
        assertThat(view.answered().get(0).answer()).isEqualTo("KNOWN");
        for (String status : List.of("SUBMITTED", "ABANDONED", "EXPIRED")) {
            saved.setStatus(status);
            assertThat(service.view(7, 42).status()).isEqualTo(status);
        }
        saved.setStatus("IN_PROGRESS");
        saved.setExpiresAt(NOW);
        assertThat(service.view(7, 42).status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void FR003_deferredViewNeverSerializesResultsEvenWhenStoredEvidenceContainsThem() throws Exception {
        service.start(command(PracticeSource.TOPIC_TEST, List.of(choice(1), choice(2)), List.of(), null, null));
        when(attempts.findByIdForUpdate(42L)).thenReturn(Optional.of(saved));
        var graded = new AttemptItem(42L, 1, "MEANING_CHOICE", "VOCAB");
        graded.setAnswer("B");
        graded.setCorrect(true);
        recorded.add(graded);
        var draft = mapper.createObjectNode();
        draft.putObject("2").putNull("answer").put("correctAnswer", "secret-pinyin").put("explanation", "secret-explanation");
        saved.setDraftAnswers(draft);
        var view = service.view(7, 42);
        assertThat(view.answered().get(0).answer()).isEqualTo("B");
        assertThat(view.answered().get(0).correct()).isNull();
        assertThat(view.answered().get(1).answer()).isNull();
        assertThat(mapper.writeValueAsString(view)).doesNotContain("correctAnswer", "\"accept\"", "explanation",
                "secret-pinyin", "secret-explanation");
    }

    @Test
    void FR003_immediateViewRestoresGradedResultsAndExcludesSkippedAndFailedItems() {
        service.start(command(PracticeSource.TOPIC_PRACTICE, List.of(choice(1), choice(2), choice(3)), List.of(), null, null));
        when(attempts.findByIdForUpdate(42L)).thenReturn(Optional.of(saved));
        for (int no = 1; no <= 3; no++) {
            var graded = new AttemptItem(42L, no, "MEANING_CHOICE", "VOCAB");
            graded.setAnswer("B");
            graded.setCorrect(no == 1 ? false : null);
            graded.setMeta(no == 1 ? Map.of() : Map.of(no == 2 ? "skipped" : "audioFailed", true));
            recorded.add(graded);
        }
        var states = service.view(7, 42).answered();
        assertThat(states.get(0).correctAnswer()).isEqualTo("A");
        assertThat(states.get(0).counted()).isTrue();
        assertThat(states.get(1).counted()).isFalse();
        assertThat(states.get(2).counted()).isFalse();
        assertThat(states.get(1).correctAnswer()).isNull();
    }

    @Test
    void FR010_getRoutingValidationAndIdentityUseThePublicAuthApi() throws Exception {
        AuthApi auth = mock(AuthApi.class);
        when(auth.currentUser()).thenReturn(new CurrentUser(7, Set.of(RoleCode.LEARNER), false));
        PracticeService practice = mock(PracticeService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PracticeController(practice, auth))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String path : List.of("/api/practice/sessions/active", "/api/practice/sessions/active?source=invalid",
                "/api/practice/sessions/wrong", "/api/practice/sessions/active?source=TOPIC_TEST&refId=bad")) {
            mvc.perform(get(path)).andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(get("/api/practice/sessions/active")
                        .param("source", "TOPIC_TEST").param("refId", "12"))
                .andExpect(status().isNoContent());
        verify(practice).findActive(7, PracticeSource.TOPIC_TEST, 12L);
        mvc.perform(get("/api/practice/sessions/active")
                        .param("source", "TOPIC_TEST"))
                .andExpect(status().isNoContent());
        verify(practice).findActive(7, PracticeSource.TOPIC_TEST, null);
        var session = new PracticeService.SessionView(42, PracticeSource.TOPIC_TEST, 12L, FeedbackMode.DEFERRED,
                "IN_PROGRESS", NOW, NOW.plusSeconds(3600), List.of(), List.of(), Map.of());
        when(practice.findActive(7, PracticeSource.TOPIC_TEST, 12L)).thenReturn(session);
        mvc.perform(get("/api/practice/sessions/active").param("source", "TOPIC_TEST").param("refId", "12"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.sessionId").value(42));
        mvc.perform(get("/api/practice/sessions/42")).andExpect(status().isOk());
        verify(practice).view(7, 42);
    }

    @Test
    void FR001_rateLimitFailureIsOpenButThe121stCounterIsNotSwallowed() {
        RateLimiter counters = mock(RateLimiter.class);
        PracticeRateLimiter limiter = new PracticeRateLimiter(counters, Clock.fixed(NOW, ZoneOffset.UTC));
        when(counters.increment(any(), any())).thenReturn(121L);
        assertThatThrownBy(() -> limiter.checkStart(7)).isInstanceOfSatisfying(ApiException.class, failure -> {
            assertThat(failure.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
            assertThat(failure.getExtra()).containsEntry("retryAfterSeconds", 1800L);
        });
        when(counters.increment(any(), any())).thenThrow(new IllegalStateException("simulated failure"));
        org.assertj.core.api.Assertions.assertThatCode(() -> limiter.checkStart(7)).doesNotThrowAnyException();
    }

    @Test
    void FR010_approvedOpenApiHasTheImplementedGetStatusesAndQuotedNoFields() throws Exception {
        String yaml = Files.readString(Path.of("specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml"));
        var parsed = new OpenAPIV3Parser().readContents(yaml);
        assertThat(parsed.getMessages()).isEmpty();
        var api = parsed.getOpenAPI();
        assertThat(api.getPaths().get("/api/practice/sessions/{id}").getGet().getResponses().keySet())
                .containsExactlyInAnyOrder("200", "401", "403", "422");
        assertThat(api.getPaths().get("/api/practice/sessions/active").getGet().getResponses().keySet())
                .containsExactlyInAnyOrder("200", "204", "401", "403", "422");
        for (String name : List.of("ServedItemView", "ItemState", "AnswerRequest", "AnswerResult")) {
            var required = new ObjectMapper(new YAMLFactory()).readTree(yaml)
                    .at("/components/schemas/" + name + "/required");
            assertThat(required.isArray()).isTrue();
            assertThat(required.toString()).contains("\"no\"").doesNotContain("false");
        }
    }

    private PracticeService service(List<PracticeSourceHandler> handlers) {
        return new PracticeService(attempts, items, mock(EntityManager.class), mapper,
                Clock.fixed(NOW, ZoneOffset.UTC), limiter, handlers);
    }

    static StartPracticeCommand command(PracticeSource source, List<ServedItem> items, List<Answer> preRecorded,
            Duration override, Map<String, Object> meta) {
        return new StartPracticeCommand(7, source, 9L, source == PracticeSource.GAME_ROUND ? "test-game" : null,
                items, preRecorded, override, meta);
    }

    static ServedItem choice(int no) {
        return new ServedItem(no, ItemType.MEANING_CHOICE, Skill.VOCAB, null,
                new ServedItem.Prompt("我们", null, null, null, null, null, null, null, null),
                List.of(new ServedItem.Option("A", "chúng tôi"), new ServedItem.Option("B", "họ")),
                "A", List.of("secret-pinyin"), "secret-explanation", new ServedItem.Rules(800, 0, false));
    }

    static ServedItem card(int no) {
        return new ServedItem(no, ItemType.STUDY_CARD, Skill.VOCAB, null,
                new ServedItem.Prompt("我", "wǒ", "ngã", "tôi", null, null, null, null, "WORD"),
                null, null, null, null, new ServedItem.Rules(1000, 0, false));
    }
}
