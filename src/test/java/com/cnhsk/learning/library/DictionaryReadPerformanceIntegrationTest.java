package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class DictionaryReadPerformanceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void NFRP02_detailP95StaysBelow150msFor5000OriginalSyntheticLexemes() throws Exception {
        String prefix = "读测014-" + UUID.randomUUID() + "-";
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < 5000; index++) rows.add(new Object[] {
                index == 0 ? "CHAR" : "WORD", prefix + index, "Mục từ đo chi tiết tự soạn " + index, 1 + index % 3
        });
        jdbc.batchUpdate("""
                INSERT INTO learning.lexemes(kind, text, meaning_vi, hsk_level, radical, stroke_count, extra)
                VALUES (?, ?, ?, ?, '口', 6, '{"examples":[{"cn":"自造句。","vi":"Câu tự soạn."}]}')
                """, rows);
        Long id = jdbc.queryForObject("SELECT id FROM learning.lexemes WHERE text=?", Long.class, prefix + "0");
        jdbc.update("""
                INSERT INTO learning.lexeme_links(parent_id, child_id, link_type)
                SELECT ?, id, 'RELATED' FROM learning.lexemes WHERE text LIKE ? AND id <> ? ORDER BY id LIMIT 250
                """, id, prefix + "%", id);
        for (int warmup = 0; warmup < 8; warmup++) lookup(id);
        long[] samples = new long[30];
        for (int sample = 0; sample < samples.length; sample++) {
            long started = System.nanoTime();
            lookup(id);
            samples[sample] = System.nanoTime() - started;
        }
        Arrays.sort(samples);
        double p95 = samples[(int) Math.ceil(samples.length * 0.95) - 1] / 1_000_000.0;
        System.out.printf(java.util.Locale.ROOT,
                "LIBRARY_DETAIL_P95 lexemes=5000 references=250 samples=%d p95_ms=%.3f%n", samples.length, p95);
        assertThat(p95).as("NFR-P02 detail p95 on 5000 synthetic lexemes (ms)").isLessThan(150.0);
    }

    private void lookup(long id) throws Exception {
        mvc.perform(get("/api/public/dictionary/lexemes/{id}", id)
                .with(request -> { request.setRemoteAddr("198.18.3.210"); return request; })).andExpect(status().isOk());
    }
}
