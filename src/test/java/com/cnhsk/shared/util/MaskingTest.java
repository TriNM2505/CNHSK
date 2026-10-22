package com.cnhsk.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingTest {

    @Test
    void FR057_email_masksLocalPartAndPreservesDomain() {
        assertThat(Masking.email("baobab@gmail.com")).isEqualTo("ba•••@gmail.com");
    }

    @Test
    void FR057_emailDoesNotRevealShortLocalPartLength() {
        assertThat(Masking.email("a@example.com")).isEqualTo("a•••@example.com");
    }

    @Test
    void FR057_emailMasksMalformedValuesInsteadOfReturningThemUnchanged() {
        assertThat(Masking.email("not-an-email")).isEqualTo("•••");
        assertThat(Masking.email("a@@example.com")).isEqualTo("•••");
    }

    @Test
    void FR057_emailPreservesNull() {
        assertThat(Masking.email(null)).isNull();
    }

    @Test
    void FR057_ipMasksLastTwoIpv4Octets() {
        assertThat(Masking.ip("203.113.18.42")).isEqualTo("203.113.x.x");
    }

    @Test
    void FR057_ipMasksIpv6BeyondTheFirst32Bits() {
        assertThat(Masking.ip("2001:db8:abcd:12::1")).isEqualTo("2001:db8:abcd:12:x:x:x:x");
    }

    @Test
    void FR057_ipDoesNotExposeInvalidAddressInput() {
        assertThat(Masking.ip("not-an-ip")).isEqualTo("x.x.x.x");
        assertThat(Masking.ip(null)).isNull();
    }
}
