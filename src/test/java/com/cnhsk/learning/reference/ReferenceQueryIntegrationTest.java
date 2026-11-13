package com.cnhsk.learning.reference;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Transactional
class ReferenceQueryIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private ReferenceQueryService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate named;
    private long creator;

    @BeforeEach
    void fixture() {
        creator = jdbc.queryForObject("INSERT INTO auth.users(email, password_hash, display_name) "
                        + "VALUES (?, ?, 'Reference fixture') RETURNING id", Long.class,
                UUID.randomUUID() + "@example.test", PASSWORD_HASH);
    }

    @Test
    void AT01_AT04_onlyPublishedItemsAreQueryable() {
        List<Long> published = new ArrayList<>();
        for (int index = 0; index < 3; index++) published.add(seed("PUBLISHED", null, null, index, "demo"));
        long draft = seed("DRAFT", null, null, 0, "demo");
        seed("DRAFT", null, null, 0, "demo");
        long hidden = seed("HIDDEN", null, null, 0, "demo");
        assertThat(ids(service.list("BOOK", null, null, 0, 20))).containsExactlyElementsOf(published);
        assertThat(service.list("BOOK", null, null, 0, 20).total()).isEqualTo(3);
        for (long id : List.of(draft, hidden, Long.MAX_VALUE)) {
            assertThatThrownBy(() -> service.detail(Long.toString(id))).isInstanceOfSatisfying(ApiException.class,
                    error -> assertThat(error.getCode()).isEqualTo(ErrorCode.REFERENCE_NOT_FOUND));
        }
        assertThat(service.detail(published.get(0).toString()).kind()).isEqualTo(ReferenceItem.Kind.BOOK);
    }

    @Test
    void AT02_openRangesIncludeOnlyTheMatchingSide() {
        long unbounded = seed("PUBLISHED", null, null, 0, "demo");
        long below3 = seed("PUBLISHED", null, 3, 0, "demo");
        long above4 = seed("PUBLISHED", 4, null, 0, "demo");
        long middle = seed("PUBLISHED", 2, 5, 0, "demo");
        assertThat(ids(service.list("BOOK", 2, null, 0, 20))).containsExactly(unbounded, below3, middle);
        assertThat(ids(service.list("BOOK", 5, null, 0, 20))).containsExactly(unbounded, above4, middle);
        assertThat(ids(service.list("BOOK", 9, null, 0, 20))).containsExactly(unbounded, above4);
        assertThat(ids(service.list("BOOK", 1, null, 0, 20))).containsExactly(unbounded, below3);
    }

    @Test
    void FR004_exactTagAndKindFiltersCannotBeUsedAsSqlOrLikePatterns() {
        long literal = seed("PUBLISHED", null, null, 0, "x_%");
        seed("PUBLISHED", null, null, 0, "x_foo");
        assertThat(ids(service.list("BOOK", null, "x_%", 0, 20))).containsExactly(literal);
        assertThat(service.list("BOOK", null, "' OR true --", 0, 20).items()).isEmpty();
        assertThat(service.list("BOOK", null, "X_%", 0, 20).items()).isEmpty();
        assertThat(service.list("PODCAST", null, null, 0, 20).items()).isEmpty();
    }

    @Test
    void FR004_paginationUsesStableSortAndLargeOffsetsWithoutOverflow() {
        long later = seed("PUBLISHED", null, null, 5, "demo");
        long first = seed("PUBLISHED", null, null, -1, "demo");
        long second = seed("PUBLISHED", null, null, -1, "demo");
        assertThat(ids(service.list("BOOK", null, null, 0, 2))).containsExactly(first, second);
        var page = service.list("BOOK", null, null, 1, 2);
        assertThat(ids(page)).containsExactly(later);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(service.list("BOOK", null, null, Integer.MAX_VALUE, 50).items()).isEmpty();
    }

    @Test
    void FR004_queryCountIsTwoRegardlessOfNumberOfItems() {
        var counted = spy(named);
        var query = new ReferenceQueryService(counted);
        seed("PUBLISHED", null, null, 0, "demo");
        query.list("BOOK", null, null, 0, 50);
        verify(counted, times(1)).queryForObject(anyString(), any(SqlParameterSource.class), any(Class.class));
        verify(counted, times(1)).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        clearInvocations(counted);
        for (int index = 0; index < 60; index++) seed("PUBLISHED", null, null, index, "demo");
        assertThat(query.list("BOOK", null, null, 0, 50).items()).hasSize(50);
        verify(counted, times(1)).queryForObject(anyString(), any(SqlParameterSource.class), any(Class.class));
        verify(counted, times(1)).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void NFR_publicListP95IsBelow150MillisecondsWith500Items() {
        for (int index = 0; index < 500; index++) seed("PUBLISHED", null, null, index, "demo");
        for (int index = 0; index < 10; index++) service.list("BOOK", null, null, 0, 20);
        List<Long> durations = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            long start = System.nanoTime();
            assertThat(service.list("BOOK", null, null, 0, 20).total()).isEqualTo(500);
            durations.add(System.nanoTime() - start);
        }
        durations.sort(Long::compareTo);
        long p95 = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        System.out.println("Reference public list p95 ms=" + p95 / 1_000_000.0);
        assertThat(p95).isLessThan(150_000_000L);
    }

    private long seed(String status, Integer min, Integer max, int order, String tag) {
        return jdbc.queryForObject("INSERT INTO learning.reference_items(kind,title,url,status,created_by,published_at,"
                        + "hsk_min,hsk_max,sort_order,tags,author) VALUES ('BOOK','Demo','https://fahasa.com/demo',?,?,?,?,?,?,"
                        + "ARRAY[?]::varchar(30)[],'Tac gia tu soan') RETURNING id", Long.class,
                status, creator, status.equals("PUBLISHED") ? Timestamp.from(Instant.parse("2026-11-13T00:00:00Z")) : null,
                min, max, order, tag);
    }

    private List<Long> ids(ReferenceQueryService.PublicReferencePage page) {
        return page.items().stream().map(ReferenceQueryService.PublicReference::id).toList();
    }
}
