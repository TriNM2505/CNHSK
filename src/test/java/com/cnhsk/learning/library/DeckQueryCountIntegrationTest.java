package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class DeckQueryCountIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 50})
    void FR014_listUsesOneSelectAndDetailAtMostTwoRegardlessOfDeckOrCardCount(int count) throws Exception {
        long user = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử SQL') RETURNING id", Long.class, UUID.randomUUID() + "@example.test");
        String authorization = "Bearer " + jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID());
        jdbc.update("INSERT INTO learning.collections(user_id,kind,name) SELECT ?,'FLASHCARD_DECK','Bộ SQL '||n FROM generate_series(1,?) n", user, count);
        List<Long> ids = jdbc.queryForList("SELECT id FROM learning.collections WHERE user_id=?", Long.class, user);
        for (long id : ids) {
            jdbc.update("INSERT INTO learning.collection_items(collection_id,title,content,order_index) SELECT ?,'Mặt trước '||n,'Mặt sau tự soạn',n FROM generate_series(1,500) n", id);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            mvc.perform(get("/api/flashcard-decks").header("Authorization", authorization))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(count));
            assertThat(statistics.getPrepareStatementCount()).as("list SELECTs with %s decks", count).isEqualTo(1);
            statistics.clear();
            if (count == 0) {
                mvc.perform(get("/api/flashcard-decks/9223372036854775807").header("Authorization", authorization)).andExpect(status().isForbidden());
                assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            } else {
                mvc.perform(get("/api/flashcard-decks/{id}", ids.get(0)).header("Authorization", authorization))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.cards.length()").value(500))
                        .andExpect(jsonPath("$.cardCount").value(500)).andExpect(jsonPath("$.dueCount").value(500));
                assertThat(statistics.getPrepareStatementCount()).as("detail SELECTs with 500 cards").isEqualTo(2);
            }
            statistics.clear();
            mvc.perform(get("/api/flashcard-decks/invalid").header("Authorization", authorization)).andExpect(status().isForbidden());
            assertThat(statistics.getPrepareStatementCount()).isZero();
        } finally { statistics.setStatisticsEnabled(previouslyEnabled); }
    }
}
