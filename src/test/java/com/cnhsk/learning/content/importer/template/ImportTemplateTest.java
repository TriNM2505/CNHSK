package com.cnhsk.learning.content.importer.template;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Yaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ImportTemplateTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void FR034_AT041_dataTypesAndTemplateOrderMatchApprovedContract() throws IOException {
        JsonNode dataTypes = contractSchemas().path("DataType").path("enum");
        List<String> expected = new ArrayList<>();
        dataTypes.forEach(value -> expected.add(value.asText()));

        assertThat(expected).containsExactly("LEXEME", "TOPIC", "TOPIC_ITEM", "GRAMMAR", "GRAMMAR_EXERCISE",
                "PRON_STAGE", "PRON_UNIT", "KNOWLEDGE_NODE", "KNOWLEDGE_EDGE", "QUESTION", "NODE_LINK");
        assertThat(ImportDataType.values()).extracting(Enum::name).containsExactlyElementsOf(expected);
        assertThat(ImportTemplate.all()).extracting(template -> template.dataType().name())
                .containsExactlyElementsOf(expected);
    }

    @ParameterizedTest(name = "FR034 AT041 {0}")
    @MethodSource("section4Templates")
    void FR034_AT041_metadataMatchesApprovedSection4(ExpectedTemplate expected) {
        ImportTemplate template = ImportTemplate.forType(expected.dataType());

        assertThat(template.label()).isEqualTo(expected.label());
        assertThat(template.naturalKey()).containsExactlyElementsOf(expected.naturalKey());
        assertThat(template.dependsOn()).containsExactlyElementsOf(expected.dependsOn());
        assertThat(template.columns()).extracting(ColumnSpec::name)
                .containsExactlyElementsOf(expected.columns());
        assertThat(template.columns().stream().filter(ColumnSpec::required).map(ColumnSpec::name).toList())
                .containsExactlyInAnyOrderElementsOf(expected.required());
        for (ColumnSpec column : template.columns()) {
            assertThat(column.maxLength()).as("%s.%s maxLength", expected.dataType(), column.name())
                    .isEqualTo(expected.maxLengths().get(column.name()));
            assertThat(column.description()).as("%s.%s guidance", expected.dataType(), column.name())
                    .isNotBlank();
        }
    }

    @ParameterizedTest(name = "FR034 JSON {0}")
    @EnumSource(ImportDataType.class)
    void FR034_AT041_serializedTemplatesMatchApprovedContractShape(ImportDataType dataType) throws IOException {
        ImportTemplate template = ImportTemplate.forType(dataType);
        JsonNode json = OBJECT_MAPPER.valueToTree(template);
        JsonNode templateSchema = contractSchemas().path("Template");
        JsonNode columnSchema = templateSchema.path("properties").path("columns").path("items");

        assertThat(propertyNames(json)).isEqualTo(propertyNames(templateSchema.path("properties")));
        assertThat(json.path("dataType").asText()).isEqualTo(dataType.name());
        assertThat(json.path("label").asText()).isEqualTo(template.label());
        assertThat(json.path("naturalKey").isArray()).isTrue();
        assertThat(json.path("dependsOn").isArray()).isTrue();
        assertThat(json.path("columns").size()).isEqualTo(template.columns().size());
        for (int index = 0; index < template.columns().size(); index++) {
            JsonNode column = json.path("columns").get(index);
            ColumnSpec definition = template.columns().get(index);
            assertThat(propertyNames(column)).isEqualTo(propertyNames(columnSchema.path("properties")));
            assertThat(column.path("name").asText()).isEqualTo(definition.name());
            assertThat(column.path("required").isBoolean()).isTrue();
            assertThat(column.path("required").asBoolean()).isEqualTo(definition.required());
            assertThat(column.path("description").isTextual()).isTrue();
            assertThat(column.path("example").isTextual()).isTrue();
            if (definition.maxLength() == null) {
                assertThat(column.path("maxLength").isNull()).isTrue();
            } else {
                assertThat(column.path("maxLength").isIntegralNumber()).isTrue();
                assertThat(column.path("maxLength").intValue()).isEqualTo(definition.maxLength());
            }
        }
    }

    @ParameterizedTest(name = "FR034 examples {0}")
    @EnumSource(ImportDataType.class)
    void FR034_exampleRowsHaveValuesWithinApprovedLengthsAndDemoLevels(ImportDataType dataType) {
        ImportTemplate template = ImportTemplate.forType(dataType);

        for (ColumnSpec column : template.columns()) {
            String example = column.example();
            assertThat(example).as("%s.%s example", dataType, column.name()).isNotNull();
            assertThat(Normalizer.isNormalized(example, Normalizer.Form.NFC)).isTrue();
            assertThat(example).doesNotContain("\uFFFD");
            if (column.required()) {
                assertThat(example).isNotBlank();
            }
            if (column.maxLength() != null) {
                assertThat(example.codePointCount(0, example.length())).isLessThanOrEqualTo(column.maxLength());
            }
            if (column.name().equals("hsk_level")) {
                assertThat(Integer.parseInt(example)).isBetween(1, 3);
                assertThat(column.description()).contains("1 đến 9");
            }
        }
    }

    @Test
    void FR034_exampleRowsReferenceTheirParentTemplateExamples() {
        Map<String, String> lexeme = exampleRow(ImportDataType.LEXEME);
        Map<String, String> topic = exampleRow(ImportDataType.TOPIC);
        Map<String, String> topicItem = exampleRow(ImportDataType.TOPIC_ITEM);
        Map<String, String> grammar = exampleRow(ImportDataType.GRAMMAR);
        Map<String, String> exercise = exampleRow(ImportDataType.GRAMMAR_EXERCISE);
        Map<String, String> stage = exampleRow(ImportDataType.PRON_STAGE);
        Map<String, String> unit = exampleRow(ImportDataType.PRON_UNIT);

        assertThat(topicItem).containsEntry("topic_code", topic.get("code"))
                .containsEntry("kind", lexeme.get("kind"))
                .containsEntry("text", lexeme.get("text"));
        assertThat(exercise).containsEntry("grammar_text", grammar.get("text"));
        assertThat(unit).containsEntry("stage_index", stage.get("stage_index"));
        assertThat(lexeme.get("kind")).isEqualTo("WORD");
        assertThat(lexeme.get("radical")).isEmpty();
        assertThat(lexeme.get("stroke_count")).isEmpty();
        assertThat(lexeme.get("example_vi")).isNotBlank();
        for (int index = 1; index <= 3; index++) {
            assertThat(grammar.get("example" + index + "_cn")).isNotBlank();
            assertThat(grammar.get("example" + index + "_vi")).isNotBlank();
        }
        for (Map<String, String> row : List.of(exercise, unit)) {
            assertThat(row.get("option_" + row.get("answer").toLowerCase(java.util.Locale.ROOT))).isNotBlank();
            assertThat(List.of(row.get("option_a"), row.get("option_b"), row.get("option_c"), row.get("option_d")))
                    .doesNotHaveDuplicates();
        }
        Map<String, String> node = exampleRow(ImportDataType.KNOWLEDGE_NODE);
        assertThat(exampleRow(ImportDataType.KNOWLEDGE_EDGE)).containsEntry("from_kid", node.get("kid"));
        assertThat(exampleRow(ImportDataType.QUESTION)).containsEntry("kid_primary", node.get("kid"));
        assertThat(exampleRow(ImportDataType.NODE_LINK)).containsEntry("kid", node.get("kid"))
                .containsEntry("target_type", lexeme.get("kind")).containsEntry("target_key", lexeme.get("text"));
    }

    @Test
    void FR002_AT02_knowledgeRowErrorsAreDeclaredWithoutChangingTheResponseShape() throws IOException {
        JsonNode schema = contractSchemas().path("RowError");
        List<String> codes = new ArrayList<>();
        schema.path("properties").path("code").path("enum").forEach(code -> codes.add(code.asText()));
        assertThat(codes).doesNotHaveDuplicates().contains("REQUIRED_MISSING", "ROW_WRITE_FAILED", "INVALID_KID",
                "EDGE_SELF", "EDGE_CYCLE", "PREREQ_LEVEL_HIGHER", "TOO_MANY_PREREQS", "EDGE_REDUNDANT",
                "EDGE_TYPE_CONFLICT", "DOMAIN_MISMATCH", "TOO_MANY_SECONDARY_KIDS", "DUPLICATE_KID",
                "GRADING_CHANGE_NOT_ALLOWED");
        assertThat(propertyNames(schema.path("properties"))).containsExactlyInAnyOrder("line", "column", "value", "code", "message");
    }

    @Test
    void FR034_sharedTemplateDefinitionsCannotBeChangedByCallers() {
        ImportTemplate original = ImportTemplate.forType(ImportDataType.TOPIC_ITEM);
        List<String> keys = new ArrayList<>(original.naturalKey());
        List<ImportDataType> dependencies = new ArrayList<>(original.dependsOn());
        List<ColumnSpec> columns = new ArrayList<>(original.columns());
        ImportTemplate copy = new ImportTemplate(original.dataType(), original.label(), keys, dependencies, columns);

        keys.clear();
        dependencies.clear();
        columns.clear();
        assertThat(copy).isEqualTo(original);
        assertThatThrownBy(() -> ImportTemplate.all().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> original.naturalKey().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> original.dependsOn().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> original.columns().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static Stream<ExpectedTemplate> section4Templates() {
        // Expected values follow spec 002 section 4, independently of the Java definitions.
        return Stream.of(
                new ExpectedTemplate(ImportDataType.LEXEME, "Chữ và từ vựng",
                        List.of("kind", "text"), List.of(),
                        List.of("kind", "text", "pinyin", "sino_viet", "meaning_vi", "hsk_level", "audio_url",
                                "radical", "stroke_count", "example_cn", "example_pinyin", "example_vi", "tags"),
                        Set.of("kind", "text", "pinyin", "meaning_vi", "hsk_level"),
                        Map.of("text", 20, "pinyin", 200, "sino_viet", 200, "meaning_vi", 1000,
                                "audio_url", 500, "radical", 1, "example_cn", 300, "example_pinyin", 500, "example_vi", 500)),
                new ExpectedTemplate(ImportDataType.TOPIC, "Chủ đề từ vựng",
                        List.of("code"), List.of(), List.of("code", "name", "hsk_level", "order_index"),
                        Set.of("code", "name", "hsk_level"), Map.of("code", 50, "name", 200)),
                new ExpectedTemplate(ImportDataType.TOPIC_ITEM, "Từ trong chủ đề",
                        List.of("topic_code", "kind", "text"), List.of(ImportDataType.TOPIC, ImportDataType.LEXEME),
                        List.of("topic_code", "kind", "text", "order_index"),
                        Set.of("topic_code", "kind", "text"), Map.of()),
                new ExpectedTemplate(ImportDataType.GRAMMAR, "Điểm ngữ pháp",
                        List.of("text"), List.of(),
                        List.of("text", "hsk_level", "title_vi", "structure", "explanation_vi", "common_mistake_vi",
                                "example1_cn", "example2_cn", "example3_cn", "example1_pinyin", "example2_pinyin",
                                "example3_pinyin", "example1_vi", "example2_vi", "example3_vi"),
                        Set.of("text", "hsk_level", "title_vi", "explanation_vi", "example1_cn", "example1_vi"),
                        Map.ofEntries(entry("text", 200), entry("title_vi", 300), entry("structure", 300),
                                entry("explanation_vi", 3000), entry("common_mistake_vi", 1000),
                                entry("example1_cn", 300), entry("example2_cn", 300), entry("example3_cn", 300),
                                entry("example1_pinyin", 500), entry("example2_pinyin", 500), entry("example3_pinyin", 500),
                                entry("example1_vi", 500), entry("example2_vi", 500), entry("example3_vi", 500))),
                new ExpectedTemplate(ImportDataType.GRAMMAR_EXERCISE, "Bài tập ngữ pháp",
                        List.of("grammar_text", "exercise_no"), List.of(ImportDataType.GRAMMAR),
                        List.of("grammar_text", "exercise_no", "stem", "option_a", "option_b", "option_c", "option_d",
                                "answer", "explanation_vi"),
                        Set.of("grammar_text", "exercise_no", "stem", "option_a", "option_b", "answer"),
                        Map.of("stem", 500, "option_a", 200, "option_b", 200, "option_c", 200,
                                "option_d", 200, "explanation_vi", 1000)),
                new ExpectedTemplate(ImportDataType.PRON_STAGE, "Tầng phát âm",
                        List.of("stage_index"), List.of(),
                        List.of("stage_index", "name", "description", "target_sounds", "pass_percent"),
                        Set.of("stage_index", "name"), Map.of("name", 100, "description", 2000)),
                new ExpectedTemplate(ImportDataType.PRON_UNIT, "Bài nghe phát âm",
                        List.of("stage_index", "unit_code"), List.of(ImportDataType.PRON_STAGE),
                        List.of("stage_index", "unit_code", "audio_url", "prompt_vi", "option_a", "option_b",
                                "option_c", "option_d", "answer", "explanation_vi"),
                        Set.of("stage_index", "unit_code", "audio_url", "option_a", "option_b", "answer"),
                        Map.of("unit_code", 30, "audio_url", 500, "prompt_vi", 200, "option_a", 50, "option_b", 50,
                                "option_c", 50, "option_d", 50, "explanation_vi", 500)),
                new ExpectedTemplate(ImportDataType.KNOWLEDGE_NODE, "Nút cây năng lực",
                        List.of("kid"), List.of(), List.of("kid", "name_vi", "name_zh", "can_do", "skill_type"),
                        Set.of("kid", "name_vi", "name_zh"), Map.of("name_vi", 200, "name_zh", 200, "can_do", 1000)),
                new ExpectedTemplate(ImportDataType.KNOWLEDGE_EDGE, "Quan hệ giữa các nút",
                        List.of("from_kid", "to_kid"), List.of(ImportDataType.KNOWLEDGE_NODE),
                        List.of("from_kid", "to_kid", "edge_type"), Set.of("from_kid", "to_kid", "edge_type"), Map.of()),
                new ExpectedTemplate(ImportDataType.QUESTION, "Câu hỏi", List.of("code"), List.of(ImportDataType.KNOWLEDGE_NODE),
                        List.of("code", "qtype", "hsk_level", "difficulty", "stem", "option_a", "option_b", "option_c",
                                "option_d", "option_e", "option_f", "correct", "explanation", "audio_url", "image_url",
                                "kid_primary", "kid_secondary", "distractor_errors"),
                        Set.of("code", "qtype", "hsk_level", "stem", "correct", "explanation", "kid_primary"),
                        Map.ofEntries(entry("code", 50), entry("stem", 2000), entry("option_a", 300), entry("option_b", 300),
                                entry("option_c", 300), entry("option_d", 300), entry("option_e", 300), entry("option_f", 300),
                                entry("explanation", 3000), entry("audio_url", 500), entry("image_url", 500))),
                new ExpectedTemplate(ImportDataType.NODE_LINK, "Gắn nội dung vào nút", List.of("target_type", "target_key"),
                        List.of(ImportDataType.KNOWLEDGE_NODE, ImportDataType.TOPIC, ImportDataType.LEXEME, ImportDataType.GRAMMAR),
                        List.of("target_type", "target_key", "kid"), Set.of("target_type", "target_key", "kid"), Map.of()));
    }

    private static JsonNode contractSchemas() throws IOException {
        Path contract = Path.of("specs", "002-learning-data-import", "contracts", "openapi-import.yaml");
        return Yaml.mapper().readTree(Files.readString(contract)).path("components").path("schemas");
    }

    private static Set<String> propertyNames(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static Map<String, String> exampleRow(ImportDataType dataType) {
        return ImportTemplate.forType(dataType).columns().stream()
                .collect(Collectors.toMap(ColumnSpec::name, ColumnSpec::example));
    }

    private record ExpectedTemplate(
            ImportDataType dataType,
            String label,
            List<String> naturalKey,
            List<ImportDataType> dependsOn,
            List<String> columns,
            Set<String> required,
            Map<String, Integer> maxLengths) {
    }
}
