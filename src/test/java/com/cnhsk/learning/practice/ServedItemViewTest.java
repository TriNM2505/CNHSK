package com.cnhsk.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ServedItemViewTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(ItemType.class)
    void FR003_AT01_eachItemTypeSerializesOnlyPublicContractFields(ItemType type) throws Exception {
        ServedItem snapshot = item(type);
        JsonNode stored = mapper.readTree(mapper.writeValueAsString(snapshot));
        JsonNode response = mapper.readTree(mapper.writeValueAsString(ServedItemView.from(snapshot)));

        assertThat(stored.path("answer").asText()).isEqualTo("A");
        assertThat(stored.path("accept").get(0).asText()).isEqualTo("wo3men5");
        assertThat(stored.path("explanation").asText()).contains("A");
        Set<String> fields = new HashSet<>();
        response.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("no", "type", "skill", "lexemeId",
                "prompt", "options", "rules");
        for (String field : fields) {
            assertThat(response.get(field)).as(field).isEqualTo(stored.get(field));
        }
        assertNoServerSecrets(response);
    }

    @Test
    void FR003_AT01_optionalFieldsCanBeAbsentWithoutDroppingRequiredFields() throws Exception {
        ServedItem.Prompt prompt = new ServedItem.Prompt(
                "我", null, null, null, null, null, null, null, null);
        ServedItem snapshot = new ServedItem(1, ItemType.STUDY_CARD, Skill.VOCAB, null,
                prompt, null, null, null, null, new ServedItem.Rules(1000, 0, false));
        JsonNode response = mapper.readTree(mapper.writeValueAsString(ServedItemView.from(snapshot)));

        assertThat(response.has("no")).isTrue();
        assertThat(response.has("type")).isTrue();
        assertThat(response.has("skill")).isTrue();
        assertThat(response.has("prompt")).isTrue();
        assertThat(response.has("rules")).isTrue();
        assertThat(response.has("lexemeId")).isFalse();
        assertThat(response.has("options")).isFalse();
        assertThat(response.path("prompt").has("pinyin")).isFalse();
        assertThat(response.path("prompt").has("examples")).isFalse();
        assertNoServerSecrets(response);
    }

    @Test
    void FR002_snapshotRoundTripRetainsServerAnswersWhileTheViewDoesNot() throws Exception {
        ServedItem original = item(ItemType.PINYIN_INPUT);
        ServedItem restored = mapper.readValue(mapper.writeValueAsString(original), ServedItem.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.answer()).isEqualTo("A");
        assertThat(restored.accept()).containsExactly("wo3men5");
        assertNoServerSecrets(mapper.readTree(mapper.writeValueAsString(ServedItemView.from(restored))));
    }

    @Test
    void FR002_snapshotCopiesOptionsAcceptedAnswersAndExamplesBeforeCallersCanMutateThem() {
        List<ServedItem.Option> options = new ArrayList<>(List.of(new ServedItem.Option("A", "tôi")));
        List<String> accept = new ArrayList<>(List.of("wo3"));
        ServedItem.Example example = new ServedItem.Example("我是学生。", "wǒ shì xuéshēng", "Tôi là học sinh.");
        List<ServedItem.Example> examples = new ArrayList<>(List.of(example));
        ServedItem.Prompt prompt = new ServedItem.Prompt(
                "我", "wǒ", null, "tôi", null, null, null, examples, "CHAR");
        ServedItem snapshot = new ServedItem(1, ItemType.PINYIN_INPUT, Skill.VOCAB, 1L,
                prompt, options, null, accept, null, new ServedItem.Rules(800, 0, false));

        options.clear();
        accept.clear();
        examples.clear();

        assertThat(snapshot.options()).containsExactly(new ServedItem.Option("A", "tôi"));
        assertThat(snapshot.accept()).containsExactly("wo3");
        assertThat(snapshot.prompt().examples()).containsExactly(example);
        assertThatThrownBy(() -> snapshot.options().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.accept().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.prompt().examples().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void FR003_publicViewCannotBeChangedThroughTheOptionsPassedToItsConstructor() {
        ServedItem snapshot = item(ItemType.MEANING_CHOICE);
        List<ServedItem.Option> options = new ArrayList<>(snapshot.options());
        ServedItemView view = new ServedItemView(snapshot.no(), snapshot.type(), snapshot.skill(),
                snapshot.lexemeId(), snapshot.prompt(), options, snapshot.rules());

        options.clear();

        assertThat(view.options()).containsExactlyElementsOf(snapshot.options());
        assertThatThrownBy(() -> view.options().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertNoServerSecrets(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(field -> {
                assertThat(field.getKey()).isNotIn("answer", "accept");
                assertNoServerSecrets(field.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(ServedItemViewTest::assertNoServerSecrets);
        }
    }

    private static ServedItem item(ItemType type) {
        ServedItem.Prompt prompt = new ServedItem.Prompt(
                "我们", "wǒmen", "ngã môn", "chúng tôi", "/audio/test.ogg", "我们是朋友。", "nghĩa",
                List.of(new ServedItem.Example("我们是朋友。", "wǒmen shì péngyou", "Chúng tôi là bạn.")), "WORD");
        return new ServedItem(1, type, Skill.VOCAB, 123L, prompt,
                List.of(new ServedItem.Option("A", "chúng tôi"), new ServedItem.Option("B", "họ")),
                "A", List.of("wo3men5"), "Đáp án đúng là A", new ServedItem.Rules(800, 3, true));
    }
}
