package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@Import(FlashcardReviewIntegrationTest.TimeConfiguration.class)
class FlashcardReviewQueryCountIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private EntityManagerFactory factory;
    @Autowired private FlashcardReviewIntegrationTest.TestClock clock;

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 120, 500})
    void dueAlwaysUsesTwoSelectsIncludingAheadFallback(int cards) throws Exception {
        clock.now = Instant.parse("2026-11-06T12:30:00Z");
        long user = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử truy vấn') RETURNING id", Long.class, UUID.randomUUID() + "@example.test");
        long deck = jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,'FLASHCARD_DECK','Bộ SQL') RETURNING id", Long.class, user);
        jdbc.update("INSERT INTO learning.collection_items(collection_id,title,content,order_index) SELECT ?,'Thẻ '||n,'Mặt sau',n FROM generate_series(1,?) n", deck, cards);
        String bearer = "Bearer " + jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID());
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            mvc.perform(get("/api/flashcard-decks/{id}/due", deck).header("Authorization", bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(Math.min(cards, 100)));
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
            jdbc.update("""
                    INSERT INTO learning.user_progress(user_id,target_type,target_id,next_review_at)
                    SELECT ?,'FLASHCARD',id,?::timestamptz+order_index*interval '1 second'
                    FROM learning.collection_items WHERE collection_id=?
                    """, user, Timestamp.from(clock.now), deck);
            statistics.clear();
            mvc.perform(get("/api/flashcard-decks/{id}/due", deck).param("ahead", "true").header("Authorization", bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(Math.min(cards, 100)));
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
            statistics.clear();
            mvc.perform(get("/api/flashcard-decks/9223372036854775807/due").header("Authorization", bearer)).andExpect(status().isForbidden());
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            statistics.clear();
            mvc.perform(get("/api/flashcard-decks/bad/due").header("Authorization", bearer)).andExpect(status().isForbidden());
            assertThat(statistics.getPrepareStatementCount()).isZero();
        } finally { statistics.setStatisticsEnabled(previous); }
    }
}
