package com.cnhsk.learning.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PiiMaskerTest {
    private final PiiMasker masker = new PiiMasker();

    @Test
    void FR005_AT05_masksEmailAndPhoneWithoutRemovingSurroundingText() {
        assertThat(masker.mask("Email tôi là a.b@gmail.com, sđt 0912 345 678"))
                .isEqualTo("Email tôi là [email], sđt [số điện thoại]");
    }

    @Test
    void FR005_masksPlusAddressAndMasksEmailBeforeItsNumericLocalPart() {
        assertThat(masker.mask("(an+demo@sub.example.test), 0912345678@example.test."))
                .isEqualTo("([email]), [email].");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0912345678", "0123 456 7890", "+84 912 345 678",
            "+84.912.345.678", "+84-123-456-7890", "0912-345-678"})
    void FR005_masksStandalonePhoneShapes(String phone) {
        assertThat(masker.mask("(" + phone + "),")).isEqualTo("([số điện thoại]),");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Năm 2026, điểm 9.5", "0912345678123456", "0912 3456 7890 1234",
            "1234-0912-345-678", "mã0912345678", "0912345678abc", "_0912345678",
            "0912345678_", "a\u03010912345678", "+849123456", "091234567", "091234567890"})
    void FR005_keepsOrdinaryNumbersAndDoesNotMaskPartialTokens(String text) {
        assertThat(masker.mask(text)).isEqualTo(text);
    }
}
