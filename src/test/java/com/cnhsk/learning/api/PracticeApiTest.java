package com.cnhsk.learning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.ToolProvider;

import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

class PracticeApiTest {

    private static final Instant NOW = Instant.parse("2026-10-30T23:30:00Z");
    private final PracticeService service = mock(PracticeService.class);
    private final PracticeApi api = new PracticeApi(service);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void FR012_publicSurfaceHasOnlyFourMethodsAndNoInternalTypesOrTransactions() {
        var methods = Arrays.stream(PracticeApi.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())).toList();
        assertThat(methods).extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("start", "answer", "finish", "view");
        assertThat(PracticeApi.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(PracticeApi.class.getConstructors()).isEmpty();
        Set<Type> visited = new HashSet<>();
        for (var method : methods) {
            assertThat(method.isAnnotationPresent(Transactional.class)).isFalse();
            assertApiType(method.getGenericReturnType(), visited);
            for (Type parameter : method.getGenericParameterTypes()) {
                assertApiType(parameter, visited);
            }
            if (!method.getName().equals("start")) {
                assertThat(method.getParameterTypes()[0]).isEqualTo(long.class);
            }
        }
        assertThat(PracticeApi.StartPracticeCommand.class.getRecordComponents()[0].getName()).isEqualTo("userId");
        assertThat(PracticeApi.class.getDeclaredFields()).hasSize(1);
        assertThat(PracticeApi.class.getDeclaredFields()[0].getType()).isEqualTo(PracticeService.class);
    }

    @Test
    void FR012_boundaryEnumsMatchTheEngineWithoutCopyingItsBusinessRules() {
        assertThat(PracticeApi.PracticeSource.values()).extracting(Enum::name)
                .containsExactly(Arrays.stream(com.cnhsk.learning.practice.PracticeSource.values())
                        .map(Enum::name).toArray(String[]::new));
        assertThat(PracticeApi.FeedbackMode.values()).extracting(Enum::name)
                .containsExactly(Arrays.stream(com.cnhsk.learning.practice.FeedbackMode.values())
                        .map(Enum::name).toArray(String[]::new));
        assertThat(PracticeApi.ItemType.values()).extracting(Enum::name)
                .containsExactly(Arrays.stream(com.cnhsk.learning.practice.ItemType.values())
                        .map(Enum::name).toArray(String[]::new));
        assertThat(PracticeApi.Skill.values()).extracting(Enum::name)
                .containsExactly(Arrays.stream(com.cnhsk.learning.practice.Skill.values())
                        .map(Enum::name).toArray(String[]::new));
    }

    @ParameterizedTest
    @EnumSource(PracticeApi.PracticeSource.class)
    void FR012_startPreservesCallerIdentityAndServerSnapshotForEverySource(PracticeApi.PracticeSource source) {
        when(service.start(any())).thenReturn(engineView(true));
        Duration ttl = source == PracticeApi.PracticeSource.GAME_ROUND ? Duration.ofSeconds(45) : null;
        var input = new PracticeApi.StartPracticeCommand(71, source, 91L, "test-game",
                List.of(item(true)), List.of(answer()), ttl, Map.of("roundSeconds", 30));

        var view = api.start(input);
        var captured = ArgumentCaptor.forClass(PracticeService.StartPracticeCommand.class);
        verify(service).start(captured.capture());
        var command = captured.getValue();
        assertThat(command.userId()).isEqualTo(71);
        assertThat(command.source().name()).isEqualTo(source.name());
        assertThat(command.refId()).isEqualTo(91);
        assertThat(command.gameCode()).isEqualTo("test-game");
        assertThat(command.ttlOverride()).isEqualTo(ttl);
        assertThat(command.sessionMeta()).containsEntry("roundSeconds", 30);
        assertThat(mapper.<JsonNode>valueToTree(command.items().get(0))).isEqualTo(mapper.valueToTree(input.items().get(0)));
        assertThat(mapper.<JsonNode>valueToTree(command.preRecorded().get(0))).isEqualTo(mapper.valueToTree(answer()));
        assertThat(mapper.<JsonNode>valueToTree(view)).isEqualTo(mapper.valueToTree(engineView(true)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR012_AT01_viewPreservesPublicFieldsAndOmitsServerSecrets(boolean rich) throws Exception {
        when(service.view(71, 42)).thenReturn(engineView(rich));
        var view = api.view(71, 42);
        assertThat(mapper.<JsonNode>valueToTree(view)).isEqualTo(mapper.valueToTree(engineView(rich)));
        var json = mapper.readTree(mapper.writeValueAsString(view));
        assertThat(json.path("items").get(0).has("answer")).isFalse();
        assertThat(json.path("items").get(0).has("accept")).isFalse();
        assertThat(json.path("answered").get(0).path("answer").asText()).isEqualTo("learner-input");
        verify(service).view(71, 42);
    }

    @Test
    void FR012_startSupportsRecordInputsWithoutOptionsOrExamples() {
        when(service.start(any())).thenReturn(engineView(false));
        var command = new PracticeApi.StartPracticeCommand(71, PracticeApi.PracticeSource.TOPIC_STUDY,
                null, null, List.of(item(false)), null, null, null);
        api.start(command);
        var captured = ArgumentCaptor.forClass(PracticeService.StartPracticeCommand.class);
        verify(service).start(captured.capture());
        assertThat(captured.getValue().items().get(0).options()).isNull();
        assertThat(captured.getValue().items().get(0).prompt().examples()).isNull();
        assertThat(captured.getValue().preRecorded()).isEmpty();
        assertThat(captured.getValue().sessionMeta()).isEmpty();
    }

    @Test
    void FR012_answerMapsAllEvidenceAndPreservesImmediateFeedback() {
        var result = new PracticeService.AnswerResult(1, true, "A", List.of("wo3"),
                "server explanation", true, null, null);
        when(service.answer(eq(71L), eq(42L), any())).thenReturn(result);
        var actual = api.answer(71, 42, answer());
        assertThat(mapper.<JsonNode>valueToTree(actual)).isEqualTo(mapper.valueToTree(result));
        verify(service).answer(71, 42, new PracticeSourceHandler.Answer(1, "B", 1200, 2,
                false, false, Map.of("evidence", "raw")));
    }

    @Test
    void FR012_AT12_deferredAnswerHasOnlyAcceptanceFields() {
        when(service.answer(eq(71L), eq(42L), any())).thenReturn(
                new PracticeService.AnswerResult(1, null, null, null, null, null, true, null));
        var json = mapper.valueToTree(api.answer(71, 42, answer()));
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.path("accepted").asBoolean()).isTrue();
        assertThat(json.has("correct")).isFalse();
        assertThat(json.has("correctAnswer")).isFalse();
        assertThat(json.has("accept")).isFalse();
        assertThat(json.has("explanation")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR012_AT14_finishReturnsServerFactsWithoutRecalculatingThem(boolean withAnswers) {
        var result = new PracticeService.FinishResult(42, "SUBMITTED", 12L, 20L, 60, true,
                List.of(new PracticeService.FinishItem(1, false, "A", "B")), Map.of("passed", false));
        when(service.finish(eq(71L), eq(42L), any())).thenReturn(result);
        var actual = api.finish(71, 42, withAnswers ? List.of(answer()) : null);
        assertThat(mapper.<JsonNode>valueToTree(actual)).isEqualTo(mapper.valueToTree(result));
        verify(service).finish(71, 42, withAnswers ? List.of(new PracticeSourceHandler.Answer(1, "B", 1200,
                2, false, false, Map.of("evidence", "raw"))) : List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"view", "answer", "finish"})
    void FR012_AT02_ownershipErrorsArePropagatedUnchangedWithTheCallersIdentity(String operation) {
        ApiException failure = new ApiException(ErrorCode.ATTEMPT_NOT_OWNED, HttpStatus.FORBIDDEN,
                "Bạn không có quyền truy cập lượt luyện này.", Map.of());
        when(service.view(72, 42)).thenThrow(failure);
        when(service.answer(eq(72L), eq(42L), any())).thenThrow(failure);
        when(service.finish(eq(72L), eq(42L), any())).thenThrow(failure);
        assertThatThrownBy(() -> {
            switch (operation) {
                case "view" -> api.view(72, 42);
                case "answer" -> api.answer(72, 42, answer());
                case "finish" -> api.finish(72, 42, List.of(answer()));
                default -> throw new AssertionError(operation);
            }
        }).isSameAs(failure);
    }

    @Test
    void FR012_dtosSnapshotCollectionsAndPreserveNullableMetadata() {
        var items = new ArrayList<>(List.of(item(true)));
        var recorded = new ArrayList<>(List.of(answer()));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("optional", null);
        var command = new PracticeApi.StartPracticeCommand(71, PracticeApi.PracticeSource.TOPIC_STUDY,
                null, null, items, recorded, null, metadata);
        var answer = new PracticeApi.Answer(1, "KNOWN", 10, 0, false, false, metadata);
        items.clear();
        recorded.clear();
        metadata.clear();
        assertThat(command.items()).hasSize(1);
        assertThat(command.preRecorded()).hasSize(1);
        assertThat(command.sessionMeta()).containsKey("optional");
        assertThat(answer.clientMeta()).containsKey("optional");
        assertThatThrownBy(() -> command.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> answer.clientMeta().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new PracticeApi.Answer(1, "KNOWN", 10, 0, false, false, null).clientMeta()).isEmpty();
    }

    @Test
    void FR012_architectureRuleAllowsApiAndRejectsInternalLearningPackages(@TempDir Path directory) throws Exception {
        String[] dependencies = {"com.cnhsk.learning.api.PracticeApi.SessionView",
                "com.cnhsk.learning.practice.PracticeService", "com.cnhsk.learning.practice.PracticeSource",
                "com.cnhsk.learning.practice.entity.Attempt", "com.cnhsk.learning.practice.entity.AttemptRepository",
                "com.cnhsk.learning.content.entity.Lexeme", "com.cnhsk.learning.future.Internal"};
        var arguments = new ArrayList<>(List.of("-classpath", System.getProperty("java.class.path"),
                "-d", directory.toString()));
        Path future = directory.resolve("Internal.java");
        Files.writeString(future, "package com.cnhsk.learning.future; public class Internal {}", StandardCharsets.UTF_8);
        arguments.add(future.toString());
        for (int i = 0; i < dependencies.length; i++) {
            Path source = directory.resolve("Consumer" + i + ".java");
            Files.writeString(source, "package com.cnhsk.community; public class Consumer" + i
                    + " { private " + dependencies[i] + " value; }", StandardCharsets.UTF_8);
            arguments.add(source.toString());
        }
        var errors = new ByteArrayOutputStream();
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, errors, arguments.toArray(String[]::new)))
                .withFailMessage(errors.toString(StandardCharsets.UTF_8)).isZero();
        var field = Class.forName("com.cnhsk.architecture.ModuleBoundaryTest")
                .getDeclaredField("FR012_community_chi_duoc_phu_thuoc_learning_api");
        field.setAccessible(true);
        ArchRule rule = (ArchRule) field.get(null);
        var failures = rule.evaluate(new ClassFileImporter().importPath(directory)).getFailureReport().getDetails();
        assertThat(failures).noneMatch(detail -> detail.contains("com.cnhsk.community.Consumer0"));
        for (int i = 1; i < dependencies.length; i++) {
            String consumer = "com.cnhsk.community.Consumer" + i;
            assertThat(failures).anyMatch(detail -> detail.contains(consumer));
        }
    }

    private static void assertApiType(Type type, Set<Type> visited) {
        if (!visited.add(type)) {
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            assertApiType(parameterized.getRawType(), visited);
            for (Type argument : parameterized.getActualTypeArguments()) {
                assertApiType(argument, visited);
            }
        } else if (type instanceof Class<?> value) {
            assertThat(value.isPrimitive() || value.getPackageName().startsWith("java.")
                    || value.getPackageName().equals("com.cnhsk.learning.api")).as(type.getTypeName()).isTrue();
            if (value.isRecord()) {
                for (var component : value.getRecordComponents()) {
                    assertApiType(component.getGenericType(), visited);
                }
            }
        } else {
            throw new AssertionError("Unverified API type: " + type);
        }
    }

    private static PracticeApi.Answer answer() {
        return new PracticeApi.Answer(1, "B", 1200, 2, false, false, Map.of("evidence", "raw"));
    }

    private static PracticeApi.ServedItem item(boolean rich) {
        return new PracticeApi.ServedItem(1, PracticeApi.ItemType.MEANING_CHOICE, PracticeApi.Skill.VOCAB, 99L,
                new PracticeApi.Prompt("我", "wǒ", "ngã", "tôi", "https://example.test/wo.mp3", "我是学生",
                        "hint", rich ? List.of(new PracticeApi.Example("我是学生", "wǒ shì xuéshēng", "Tôi là học sinh"))
                                : null, "CHAR"),
                rich ? List.of(new PracticeApi.Option("A", "tôi"), new PracticeApi.Option("B", "bạn")) : null,
                "A", rich ? List.of("secret-pinyin") : null, "secret-explanation", new PracticeApi.Rules(800, 3, true));
    }

    private static PracticeService.SessionView engineView(boolean rich) {
        var prompt = new com.cnhsk.learning.practice.ServedItem.Prompt("我", "wǒ", "ngã", "tôi",
                "https://example.test/wo.mp3", "我是学生", "hint", rich ? List.of(
                        new com.cnhsk.learning.practice.ServedItem.Example("我是学生", "wǒ shì xuéshēng", "Tôi là học sinh"))
                        : null, "CHAR");
        var item = new com.cnhsk.learning.practice.ServedItemView(1,
                com.cnhsk.learning.practice.ItemType.MEANING_CHOICE, com.cnhsk.learning.practice.Skill.VOCAB,
                99L, prompt, rich ? List.of(new com.cnhsk.learning.practice.ServedItem.Option("A", "tôi")) : null,
                new com.cnhsk.learning.practice.ServedItem.Rules(800, 3, true));
        return new PracticeService.SessionView(42, com.cnhsk.learning.practice.PracticeSource.TOPIC_PRACTICE,
                91L, com.cnhsk.learning.practice.FeedbackMode.IMMEDIATE, "IN_PROGRESS", NOW, NOW.plusSeconds(7200),
                List.of(item), List.of(new PracticeService.ItemState(1, "learner-input", rich ? true : null,
                        rich ? "A" : null, rich)), Map.of("mixedDistractors", true));
    }
}
