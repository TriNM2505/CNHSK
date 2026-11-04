package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class DictionarySearchPerformanceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void NFRP01_searchP95StaysBelow300msFor5000OriginalSyntheticLexemes() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < 5000; index++) rows.add(new Object[] {
                "测014-" + String.format(java.util.Locale.ROOT, "%04d", index), "Mục từ đo014 số " + index,
                "muc tu do014 so " + index, 1 + index % 3, index
        });
        jdbc.batchUpdate("""
                INSERT INTO learning.lexemes(kind, text, pinyin, pinyin_norm, meaning_vi, meaning_norm, hsk_level, frequency)
                VALUES ('WORD', ?, 'cè shì', 'ceshi', ?, ?, ?, ?)
                """, rows);

        String[][] cases = {{"HANZI", "测014-0123"}, {"PINYIN", "ce4shi4"}, {"MEANING", "mục từ đo014"}};
        for (int mode = 0; mode < cases.length; mode++) {
            String[] query = cases[mode];
            String address = "203.0.113." + (210 + mode);
            for (int warmup = 0; warmup < 8; warmup++) lookup(query, address);
            long[] samples = new long[30];
            for (int sample = 0; sample < samples.length; sample++) {
                long started = System.nanoTime();
                lookup(query, address);
                samples[sample] = System.nanoTime() - started;
            }
            Arrays.sort(samples);
            double p95 = samples[(int) Math.ceil(samples.length * 0.95) - 1] / 1_000_000.0;
            System.out.printf(java.util.Locale.ROOT, "LIBRARY_SEARCH_P95 type=%s lexemes=5000 samples=%d p95_ms=%.3f%n",
                    query[0], samples.length, p95);
            assertThat(p95).as("NFR-P01 %s p95 on 5000 synthetic lexemes (ms)", query[0]).isLessThan(300.0);
        }
    }

    private void lookup(String[] query, String address) throws Exception {
        mvc.perform(get("/api/public/dictionary/search").param("type", query[0]).param("q", query[1])
                .with(request -> { request.setRemoteAddr(address); return request; })).andExpect(status().isOk());
    }
}
