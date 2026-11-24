package com.cnhsk.learning.content.importer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.ErrorCodeRowValidator;
import com.cnhsk.learning.content.importer.validate.KnowledgeNodeRowValidator;
import com.cnhsk.learning.content.importer.validate.LearningTaskRowValidator;
import com.cnhsk.learning.content.importer.validate.NodeLinkRowValidator;
import com.cnhsk.learning.content.importer.validate.RowValidationResult;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.importer.write.ErrorCodeRowWriter;
import com.cnhsk.learning.content.importer.write.KnowledgeNodeRowWriter;
import com.cnhsk.learning.content.importer.write.LearningTaskRowWriter;
import com.cnhsk.learning.content.importer.write.NodeLinkRowWriter;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.knowledge.entity.LearningTask;
import com.cnhsk.learning.knowledge.repository.ErrorCodeRepository;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.LearningTaskRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Competency tree v2.0 attributes round-trip through the importer and the real schema (V18). */
class CompetencyTreeV2ImportIntegrationTest extends AbstractIntegrationTest {
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private KnowledgeNodeRowValidator nodeValidator;
    @Autowired private KnowledgeNodeRowWriter nodeWriter;
    @Autowired private ErrorCodeRowValidator errorValidator;
    @Autowired private ErrorCodeRowWriter errorWriter;
    @Autowired private LearningTaskRowValidator taskValidator;
    @Autowired private LearningTaskRowWriter taskWriter;
    @Autowired private NodeLinkRowValidator linkValidator;
    @Autowired private NodeLinkRowWriter linkWriter;
    @Autowired private KnowledgePointRepository points;
    @Autowired private ErrorCodeRepository errorCodes;
    @Autowired private LearningTaskRepository tasks;
    @Autowired private PronStageRepository stages;

    @Test
    void nodeSkillsDetailErrorCodesTasksAndPronunciationLinksPersist() {
        int n = ThreadLocalRandom.current().nextInt(100, 999);
        String np = "NP7-" + n, nl = "NL7-" + n, na = "NA7-" + n, tv = "TV7-" + n;
        try {
            persistAndCheck(n, np, nl, na, tv);
        } finally {
            // The context is shared with tree-wide tests (empty tree, node counts), so nothing created here may remain.
            List<String> kids = List.of(np, nl, na, tv);
            String in = "(SELECT id FROM learning.knowledge_points WHERE code IN (?, ?, ?, ?))";
            jdbc.update("DELETE FROM learning.learning_tasks WHERE nl_kid_id IN " + in, kids.toArray());
            jdbc.update("DELETE FROM learning.error_codes WHERE code IN (?, ?) OR kid_id IN " + in,
                    java.util.stream.Stream.concat(java.util.stream.Stream.of("E-NP-" + (n % 90 + 10), "E-NL-" + (n % 90 + 10)), kids.stream()).toArray());
            jdbc.update("DELETE FROM learning.knowledge_points WHERE code IN (?, ?, ?, ?)", kids.toArray());
        }
    }

    private void persistAndCheck(int n, String np, String nl, String na, String tv) {
        for (String kid : List.of(np, nl, na, tv)) {
            write(nodeValidator.validate(read(Map.of("kid", kid, "name_vi", "Nút " + kid, "name_zh", "节点",
                    "skills", "reading|SPEAKING", "pattern", "A + 是 + B",
                    "examples_ok", "[{\"cn\":\"我是学生。\",\"vi\":\"Tôi là học sinh.\"}]"))), nodeWriter::write);
        }
        var node = points.findByCode(np).orElseThrow();
        assertThat(node.getSkills()).containsExactly("READING", "SPEAKING");
        assertThat(node.getPattern()).isEqualTo("A + 是 + B");
        assertThat(node.getExamples().ok()).singleElement().satisfies(e -> assertThat(e.cn()).isEqualTo("我是学生。"));
        assertThat(write(nodeValidator.validate(read(Map.of("kid", np, "name_vi", "Nút " + np, "name_zh", "节点",
                "skills", "SPEAKING|READING"))), nodeWriter::write)).isEqualTo(RowAction.UNCHANGED);

        String code = "E-NP-" + (n % 90 + 10);
        assertThat(write(errorValidator.validate(read(Map.of("code", code, "kid", np, "severity", "2", "name_vi", "Thừa 是"))),
                errorWriter::write)).isEqualTo(RowAction.CREATE);
        assertThat(errorCodes.findById(code)).get().satisfies(e -> {
            assertThat(e.getKid().getCode()).isEqualTo(np);
            assertThat(e.countsAsEvidence()).isTrue();
        });
        String nlCode = "E-NL-" + (n % 90 + 10);
        assertThat(write(errorValidator.validate(read(Map.of("code", nlCode, "severity", "1", "name_vi", "Sai xưng hô"))),
                errorWriter::write)).isEqualTo(RowAction.CREATE);
        assertThat(errorCodes.findById(nlCode)).get().satisfies(e -> {
            assertThat(e.getKid()).isNull();
            assertThat(e.countsAsEvidence()).isFalse();
        });

        String taskCode = "TASK_" + n;
        Map<String, String> task = Map.of("code", taskCode, "kind", "SPEAKING", "hsk_level", "1", "nl_kid", nl,
                "title_vi", "Tự giới thiệu", "prompt_vi", "Giới thiệu bản thân.", "targets",
                "C4:" + np + "|C2:" + na + "|C3:" + tv, "max_seconds", "60");
        assertThat(write(taskValidator.validate(read(task)), taskWriter::write)).isEqualTo(RowAction.CREATE);
        assertThat(write(taskValidator.validate(read(task)), taskWriter::write)).isEqualTo(RowAction.UNCHANGED);
        LearningTask saved = transactions.execute(status -> {
            LearningTask found = tasks.findByCodeIn(List.of(taskCode)).get(0);
            found.getTargets().size();
            return found;
        });
        assertThat(saved.targetsCell()).isEqualTo("C2:" + na + "|C3:" + tv + "|C4:" + np);
        assertThat(saved.getNlKid().getCode()).isEqualTo(nl);

        short stageIndex = 8;
        boolean stageExisted = stages.findByStageIndex(stageIndex).isPresent();
        PronStage stage = stages.findByStageIndex(stageIndex).orElseGet(() -> new PronStage(stageIndex, "Tầng thử"));
        List<PronUnit> original = List.copyOf(stage.getUnits());
        List<PronUnit> units = new java.util.ArrayList<>(original);
        units.add(new PronUnit("U" + n, "https://example.com/a.mp3", "Nghe", List.of(
                new PronUnit.Option("A", "ba"), new PronUnit.Option("B", "pa")), "A", null));
        stage.setUnits(units);
        stage = stages.saveAndFlush(stage);
        try {
            String key = stageIndex + ":U" + n;
            assertThat(write(linkValidator.validate(read(Map.of("target_type", "PRON_UNIT", "target_key", key, "kid", na))),
                    linkWriter::write)).isEqualTo(RowAction.UPDATE);
            String stored = jdbc.queryForObject("SELECT units::text FROM learning.pron_stages WHERE stage_index = 8", String.class);
            assertThat(stored).contains("\"kid\": \"" + na + "\"").contains("\"code\": \"U" + n + "\"");
            assertThat(write(linkValidator.validate(read(Map.of("target_type", "PRON_UNIT", "target_key", key, "kid", na))),
                    linkWriter::write)).isEqualTo(RowAction.UNCHANGED);
            RowValidationResult wrongDomain = linkValidator.validate(read(Map.of("target_type", "PRON_UNIT", "target_key", key, "kid", np)));
            assertThat(wrongDomain.errors()).anySatisfy(e -> assertThat(e.code()).isEqualTo("DOMAIN_MISMATCH"));
        } finally {
            PronStage restore = stages.findByStageIndex(stageIndex).orElseThrow();
            if (stageExisted) {
                restore.setUnits(original);
                stages.saveAndFlush(restore);
            } else {
                // Other tests create stages 1–8 themselves and expect none to exist.
                stages.delete(restore);
                stages.flush();
            }
        }
    }

    private RowAction write(RowValidationResult result, java.util.function.Function<ValidatedRow, RowAction> writer) {
        assertThat(result.errors()).isEmpty();
        return transactions.execute(status -> writer.apply(result.validRows().get(0)));
    }

    private static CsvReader.ReadResult read(Map<String, String> values) {
        return new CsvReader.ReadResult(1, List.of(new CsvReader.Row(2, new LinkedHashMap<>(values))), List.of());
    }
}
