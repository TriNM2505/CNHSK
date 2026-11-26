package com.cnhsk.learning.content.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.cnhsk.learning.knowledge.QuestionRules;
import com.cnhsk.learning.knowledge.QuestionRules.Option;
import com.cnhsk.learning.knowledge.QuestionRules.Violation;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Picture options of HSK-format papers are imported as KEY:URL pairs. */
class QuestionImportPicturesTest {
    private static final List<Option> OPTIONS = List.of(new Option("A", "ảnh: mèo", true, null, null),
            new Option("B", "ảnh: chó", false, null, null));

    @Test
    void picturesAttachToTheirOptions() {
        List<Violation> errors = new ArrayList<>();
        var options = QuestionImportSupport.withPictures(OPTIONS, "A:https://img.test/cat.png | B:https://img.test/dog.png", errors);
        assertThat(errors).isEmpty();
        assertThat(options).extracting(Option::imageUrl).containsExactly("https://img.test/cat.png", "https://img.test/dog.png");
    }

    @Test
    void unknownOrRepeatedKeysAreRejected() {
        for (String cell : List.of("C:https://img.test/x.png", "A:https://img.test/x.png|A:https://img.test/y.png", "A", "A:")) {
            List<Violation> errors = new ArrayList<>();
            QuestionImportSupport.withPictures(OPTIONS, cell, errors);
            assertThat(errors).extracting(Violation::field).containsExactly("option_images");
        }
    }

    @Test
    void pictureQuestionNeedsAStemPictureOrAPictureOnEveryOption() {
        var pictured = OPTIONS.stream().map(option -> new Option(option.key(), option.text(), option.isCorrect(), null, null,
                "https://img.test/" + option.key() + ".png")).toList();
        var ok = new QuestionRules.Draft(QuestionType.IMAGE_MATCH, 1, 2, "他在做什么？", pictured, null, "Lời giải", null, null,
                "TV1-001", List.of());
        assertThat(QuestionRules.validate(ok, Set.of("TV1-001")).errors()).isEmpty();

        var partly = new QuestionRules.Draft(QuestionType.IMAGE_MATCH, 1, 2, "他在做什么？",
                List.of(pictured.get(0), OPTIONS.get(1)), null, "Lời giải", null, null, "TV1-001", List.of());
        assertThat(QuestionRules.validate(partly, Set.of("TV1-001")).errors()).extracting(Violation::field).contains("imageUrl");
    }
}
