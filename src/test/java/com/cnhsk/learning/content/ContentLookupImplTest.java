package com.cnhsk.learning.content;

import java.sql.ResultSet;
import java.util.List;
import java.util.Set;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.learning.api.ContentLookup.GameContentQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContentLookupImplTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final ContentLookupImpl lookup = new ContentLookupImpl(jdbc);

    @Test
    void FR020_nullQueryIsRejectedBeforeReadingTheDatabase() {
        assertThatIllegalArgumentException().isThrownBy(() -> lookup.pool(null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void FR021_usesNumberedPinyinAndAllNormalizedMeaningFragmentsWithoutChangingDisplay() throws Exception {
        ResultSet row = row("  nǚ hái  ", " cô gái, thiếu nữ ; nghĩa khác ", "co gai, thieu nu; co gai ; ;");
        project(row);
        var card = lookup.pool(query()).get(0);
        assertThat(card.pinyin()).isEqualTo("  nǚ hái  ");
        assertThat(card.pinyinKey()).isEqualTo("nv3hai2");
        assertThat(card.meaningShort()).isEqualTo("cô gái, thiếu nữ");
        assertThat(card.meaningKeys()).containsExactlyInAnyOrder("co gai", "thieu nu");
        assertThat(card.id()).isEqualTo(91);
        assertThat(card.kind()).isEqualTo("WORD");
        assertThat(card.text()).isEqualTo("女孩");
        assertThat(card.radical()).isNull();
        assertThat(card.strokeCount()).isNull();
        assertThat(card.audioUrl()).isEqualTo("https://example.test/audio.mp3");
        assertThat(card.hskLevel()).isEqualTo(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t", "\u00A0"})
    void FR021_missingNormalizedMeaningFallsBackToTheExistingNormalizer(String normalized) throws Exception {
        project(row("zì", " chữ ; từ, chữ ; ", normalized));
        var card = lookup.pool(query()).get(0);
        assertThat(card.meaningKeys()).containsExactlyInAnyOrder("chu", "tu");
        assertThat(card.meaningShort()).isEqualTo("chữ");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t", "\u00A0"})
    void FR021_absentPinyinKeepsTheCardWithANullKey(String pinyin) throws Exception {
        project(row(pinyin, "chữ", "chu"));
        var cards = lookup.pool(query());
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).pinyinKey()).isNull();
    }

    @Test
    void FR021_malformedPinyinKeepsTheCardAndOnlyLogsAFixedWarning() throws Exception {
        String pinyin = "invalid-private-pinyin-123";
        project(row(pinyin, "nghĩa tự soạn", "nghia tu soan"));
        Logger logger = (Logger) LoggerFactory.getLogger(ContentLookupImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>(); appender.start(); logger.addAppender(appender);
        try {
            var cards = lookup.pool(query());
            assertThat(cards).hasSize(1);
            assertThat(cards.get(0).pinyinKey()).isNull();
            assertThat(cards.get(0).pinyin()).isEqualTo(pinyin);
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).isEqualTo("Invalid stored pinyin in game content; pronunciation key unavailable.");
                assertThat(event.getFormattedMessage()).doesNotContain(pinyin, "nghĩa tự soạn");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    private ResultSet row(String pinyin, String meaning, String normalized) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(91L);
        when(row.getString("kind")).thenReturn("WORD");
        when(row.getString("text")).thenReturn("女孩");
        when(row.getString("pinyin")).thenReturn(pinyin);
        when(row.getString("meaning_vi")).thenReturn(meaning);
        when(row.getString("meaning_norm")).thenReturn(normalized);
        when(row.getString("audio_url")).thenReturn("https://example.test/audio.mp3");
        when(row.getObject("hsk_level", Integer.class)).thenReturn(1);
        return row;
    }

    private void project(ResultSet row) {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenAnswer(call -> {
            SqlParameterSource parameters = call.getArgument(1);
            assertThat(parameters.getValue("kinds")).isEqualTo(Set.of("WORD"));
            assertThat(parameters.getValue("limit")).isEqualTo(20);
            RowMapper<?> mapper = call.getArgument(2);
            return List.of(mapper.mapRow(row, 0));
        });
    }

    private GameContentQuery query() {
        return new GameContentQuery(Set.of("WORD"), null, false, false, false, null, 20);
    }
}
