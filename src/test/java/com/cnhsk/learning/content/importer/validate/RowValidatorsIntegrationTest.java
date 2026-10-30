package com.cnhsk.learning.content.importer.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.CsvReader.RowError;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RowValidatorsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CsvReader reader;
    @Autowired private LexemeRowValidator lexemeValidator;
    @Autowired private TopicRowValidator topicValidator;
    @Autowired private TopicItemRowValidator itemValidator;
    @Autowired private LexemeRepository lexemes;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository items;

    @Test
    void FR011_existingPostgresReferencesAreReadWithoutWritingAnyContent() {
        String code = code();
        topics.saveAndFlush(new Topic(code, "Chủ đề tự soạn", (short) 1));
        lexemes.saveAndFlush(new Lexeme(LexemeKind.WORD, "我们", "chúng ta"));
        long topicCount = topics.count();
        long lexemeCount = lexemes.count();
        long itemCount = items.count();

        RowValidationResult result = itemValidator.validate(read(ImportDataType.TOPIC_ITEM,
                "topic_code,kind,text,order_index\r\n " + code + " , WORD , 我们 ,7\r\n"));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows()).hasSize(1);
        assertThat(result.validRows().get(0).values()).containsEntry("topic_code", code)
                .containsEntry("kind", "WORD").containsEntry("text", "我们").containsEntry("order_index", "7");
        assertThat(topics.count()).isEqualTo(topicCount);
        assertThat(lexemes.count()).isEqualTo(lexemeCount);
        assertThat(items.count()).isEqualTo(itemCount);
    }

    @Test
    void FR011_AT014_referenceLookupKeepsKindAndDoesNotCreateMissingParents() {
        String code = code();
        topics.saveAndFlush(new Topic(code, "Chủ đề tự soạn", (short) 1));
        lexemes.saveAndFlush(new Lexeme(LexemeKind.WORD, "我", "tôi"));

        RowValidationResult result = itemValidator.validate(read(ImportDataType.TOPIC_ITEM,
                "topic_code,kind,text\r\n" + code + ",CHAR,我\r\n"));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.column()).isEqualTo("text");
            assertThat(error.code()).isEqualTo("REFERENCE_NOT_FOUND");
            assertThat(error.message()).contains("Chữ và từ vựng trước");
        });
        assertThat(lexemes.findByKindAndText(LexemeKind.CHAR, "我")).isEmpty();
    }

    @Test
    void FR011_referenceInputIsParameterizedAndOnlyReportsMissingReferences() {
        String existingCode = code();
        topics.saveAndFlush(new Topic(existingCode, "Chủ đề an toàn", (short) 1));

        RowValidationResult result = itemValidator.validate(read(ImportDataType.TOPIC_ITEM,
                "topic_code,kind,text\r\n' OR 1=1 --,WORD,不存在\r\n"));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).extracting(RowError::code).containsOnly("REFERENCE_NOT_FOUND");
        assertThat(result.errors()).extracting(RowError::column).containsExactly("topic_code", "text");
        assertThat(topics.findByCode(existingCode)).isPresent();
    }

    @Test
    void FR009_FR010_csvStructuralAndFieldErrorsCoexistWithLaterValidRows() {
        String csv = "kind,text,pinyin,meaning_vi,hsk_level,tags\n"
                + "WORD,我们,wǒmen,chúng ta,10,a||b\n"
                + "WORD,我,wǒ\n"
                + "\n\n\n"
                + "WORD,我们,wǒmen,chúng ta,1,a| |b\n"
                + "WORD,我们,wǒmen,chúng ta,1,|a|\n";

        RowValidationResult result = lexemeValidator.validate(read(ImportDataType.LEXEME, csv));

        assertThat(result.totalRows()).isEqualTo(4);
        assertThat(result.validRows()).singleElement().satisfies(row -> {
            assertThat(row.line()).isEqualTo(7);
            assertThat(row.values()).containsEntry("tags", "a|b");
            assertThat(row.pinyinNorm()).isEqualTo("women");
        });
        assertThat(result.errors()).extracting(RowError::line).containsExactly(2, 3, 8);
        assertThat(result.errors()).extracting(RowError::code)
                .containsExactly("INVALID_HSK_LEVEL", "INVALID_VALUE", "DUPLICATE_IN_FILE");
        assertThat(result.errors().get(2).message()).isEqualTo("Trùng khoá với dòng 7");
    }

    @Test
    void FR010_topicItemOnlyReservesKeyAfterAllFieldAndReferenceChecks() {
        String code = code();
        topics.saveAndFlush(new Topic(code, "Chủ đề tự soạn", (short) 1));
        lexemes.saveAndFlush(new Lexeme(LexemeKind.WORD, "我们", "chúng ta"));
        List<CsvReader.Row> rows = List.of(
                new CsvReader.Row(3, Map.of("topic_code", code, "kind", "WORD", "text", "我们", "order_index", "-1")),
                new CsvReader.Row(7, Map.of("topic_code", code, "kind", "WORD", "text", "我们", "order_index", "0")),
                new CsvReader.Row(9, Map.of("topic_code", code, "kind", "WORD", "text", "我们", "order_index", "2")));

        RowValidationResult result = itemValidator.validate(new CsvReader.ReadResult(3, rows, List.of()));

        assertThat(result.validRows()).extracting(RowValidationResult.ValidatedRow::line).containsExactly(7);
        assertThat(result.errors()).extracting(RowError::line).containsExactly(3, 9);
        assertThat(result.errors()).extracting(RowError::code).containsExactly("INVALID_VALUE", "DUPLICATE_IN_FILE");
        assertThat(result.errors().get(1).message()).isEqualTo("Trùng khoá với dòng 7");
    }

    @Test
    void FR010_topicValidationUsesNormalizedNaturalKeyAndKeepsBlankOrderIndex() {
        String code = code();
        RowValidationResult result = topicValidator.validate(read(ImportDataType.TOPIC,
                "code,name,hsk_level,order_index\n " + code + " ,Chủ   đề,1, \n" + code + ",Chủ đề khác,1,3\n"));

        assertThat(result.validRows()).singleElement().satisfies(row -> assertThat(row.values())
                .containsEntry("code", code).containsEntry("name", "Chủ đề").containsEntry("order_index", ""));
        assertThat(result.errors()).containsExactly(new RowError(3, null, null, "DUPLICATE_IN_FILE", "Trùng khoá với dòng 2"));
        assertThat(topics.findByCode(code)).isEmpty();
    }

    private CsvReader.ReadResult read(ImportDataType type, String csv) {
        return reader.read(type, "self-authored.csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    private static String code() {
        return "TEST_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }
}
