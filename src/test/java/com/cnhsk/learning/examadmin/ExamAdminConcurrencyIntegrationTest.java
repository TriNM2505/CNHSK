package com.cnhsk.learning.examadmin;

import static com.cnhsk.learning.examadmin.ExamAdminModels.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ExamAdminConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired private ExamAdminService service;
    @Autowired private JdbcTemplate jdbc;
    private ExamAdminFixture fixture;

    @BeforeEach void setup() { fixture = new ExamAdminFixture(jdbc); }
    @AfterEach void cleanup() { fixture.close(); }

    @Test
    void twoEditorsUsingTheSameEtagCannotOverwriteEachOther() throws Exception {
        var input = fixture.input(2, 1);
        var original = service.create(fixture.actor, input);
        var first = new ExamWrite(input.code(), "Người sửa một", 3, 2, input.sections(), input.questions());
        var second = new ExamWrite(input.code(), "Người sửa hai", 3, 2, input.sections(), List.of(input.questions().get(1), input.questions().get(0)));
        assertThat(race(() -> service.update(original.id(), () -> ExamAdminServiceTest.update(first, original.etag())),
                () -> service.update(original.id(), () -> ExamAdminServiceTest.update(second, original.etag()))))
                .containsExactlyInAnyOrder("OK", "EDIT_CONFLICT");
        var saved = service.detail(original.id());
        assertThat(saved.etag()).isNotEqualTo(original.etag());
        assertThat(saved.title()).isIn(first.title(), second.title());
        List<Long> expected = (saved.title().equals(first.title()) ? first : second).questions().stream().map(QuestionWrite::questionId).toList();
        assertThat(saved.questions()).extracting(QuestionView::questionId).containsExactlyElementsOf(expected);
    }

    @Test
    void simultaneousCreatesWithTheSameCodeProduceOneCompleteDraftAndOneConflict() throws Exception {
        var input = fixture.input(2, 1);
        long other = fixture.user();
        assertThat(race(() -> service.create(fixture.actor, input), () -> service.create(other, input)))
                .containsExactlyInAnyOrder("OK", "EXAM_CODE_TAKEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.exams WHERE code=?", Integer.class, input.code())).isEqualTo(1);
        long id = jdbc.queryForObject("SELECT id FROM learning.exams WHERE code=?", Long.class, input.code());
        assertThat(service.detail(id).questions()).hasSize(2);
    }

    private List<String> race(Callable<?> first, Callable<?> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var futures = java.util.stream.Stream.of(first, second).map(action -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try { action.call(); return "OK"; }
                catch (ApiException exception) { return exception.getCode().name(); }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
