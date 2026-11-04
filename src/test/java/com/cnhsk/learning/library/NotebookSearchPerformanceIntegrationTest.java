package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class NotebookSearchPerformanceIntegrationTest extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private MockMvc mvc;

    @Test
    void NFRP04_searchP95Below200msOn1000OriginalNotesUsingExistingTrigramIndex() throws Exception {
        assertThat(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname='pg_trgm')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname='learning' AND indexname='ix_item_search'", String.class))
                .contains("USING gin", "content", "gin_trgm_ops");
        long user = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người đo sổ tay') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test");
        long book = jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,'NOTEBOOK','Sổ tay') RETURNING id", Long.class, user);
        jdbc.update("""
                INSERT INTO learning.collection_items(collection_id,title,content,tags)
                SELECT ?, 'Ghi chú đo tự soạn ' || n, repeat('Nội dung tự soạn. ',20) || 'gia đình số ' || n,
                       ARRAY['hsk1'] FROM generate_series(1,1000) n
                """, book);
        String bearer = "Bearer " + jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID());
        for (int warmup = 0; warmup < 8; warmup++) lookup(bearer);
        long[] samples = new long[30];
        for (int sample = 0; sample < samples.length; sample++) {
            long start = System.nanoTime();
            lookup(bearer);
            samples[sample] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        double p95 = samples[(int) Math.ceil(samples.length * 0.95) - 1] / 1_000_000.0;
        System.out.printf(java.util.Locale.ROOT, "NOTEBOOK_SEARCH_P95 notes=1000 samples=30 p95_ms=%.3f%n", p95);
        assertThat(p95).isLessThan(200.0);
    }

    private void lookup(String bearer) throws Exception {
        mvc.perform(get("/api/notes").param("q", "gia đình").param("tag", "hsk1").header("Authorization", bearer)).andExpect(status().isOk());
    }
}
