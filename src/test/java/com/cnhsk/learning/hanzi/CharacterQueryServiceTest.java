package com.cnhsk.learning.hanzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CharacterQueryServiceTest {
    @Mock CharacterQueryRepository characters;
    @InjectMocks CharacterQueryService service;

    @Test
    void FR001_AT01_listsMatchingCharactersWithPaging() {
        Lexeme character = character(1L, "好", "hǎo", (short) 7);
        when(characters.search(eq(LexemeKind.CHAR), eq((short) 1), eq("好"), any(), any()))
                .thenReturn(new PageImpl<>(List.of(character)));
        var page = service.list(1L, "好", 0, 20);
        assertThat(page.items()).extracting(CharacterQueryService.CharacterSummary::text).containsExactly("好");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void FR001_AT01_findsPinyinPrefixAndRejectsSizeAboveLimit() {
        Lexeme character = character(3L, "好", "hǎo", (short) 7);
        when(characters.search(eq(LexemeKind.CHAR), eq((short) 1), eq("hao"), eq("hao"), any()))
                .thenReturn(new PageImpl<>(List.of(character)));

        var page = service.list(1L, "hao", 0, 20);

        assertThat(page.items()).extracting(CharacterQueryService.CharacterSummary::text).containsExactly("好");
        assertThatThrownBy(() -> service.list(1L, "hao", 0, 51))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void FR002_FR003_returnsWritingAvailabilityAndHistory() {
        Lexeme character = character(2L, "字", null, null);
        when(characters.findCharacter(2L)).thenReturn(character);
        when(characters.recallUnlocked(8L, 2L)).thenReturn(true);
        when(characters.lastPracticedAt(8L, 2L)).thenReturn(java.time.Instant.parse("2026-10-31T00:00:00Z"));
        var detail = service.detail(8L, 2L);
        assertThat(detail.writing().strokeAvailable()).isFalse();
        assertThat(detail.writing().recallUnlocked()).isTrue();
        assertThat(detail.writing().dictationAvailable()).isFalse();
    }

    private Lexeme character(long id, String text, String pinyin, Short strokes) {
        Lexeme lexeme = new Lexeme(LexemeKind.CHAR, text, "nghĩa");
        ReflectionTestUtils.setField(lexeme, "id", id);
        lexeme.setHskLevel((short) 1);
        lexeme.setPinyin(pinyin);
        lexeme.setStrokeCount(strokes);
        return lexeme;
    }
}
