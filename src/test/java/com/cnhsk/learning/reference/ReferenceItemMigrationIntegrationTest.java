package com.cnhsk.learning.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

@Transactional
class ReferenceItemMigrationIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Set<String> INSERT_COLUMNS = Set.of("kind", "title", "description", "url", "thumbnail_url",
            "hsk_min", "hsk_max", "tags", "author", "publisher", "published_year", "is_affiliate", "status",
            "sort_order", "created_by", "published_at", "created_at", "updated_at");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReferenceItemRepository references;
    @Autowired private EntityManager entities;

    private long creator;

    @BeforeEach
    void createDisposableAuthor() {
        creator = jdbc.queryForObject("INSERT INTO auth.users(email, password_hash, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, "reference-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Nguoi tao tu soan");
    }

    @Test
    void FR018_AT09_migrationMatchesTheApprovedDdlExactly() throws Exception {
        String fence = String.valueOf((char) 96).repeat(3);
        String source = Files.readString(Path.of("specs/018-reference-resources/spec.md"));
        var sql = Pattern.compile("(?s)### 0\\.1.*?" + fence + "sql\\R(.*?)" + fence).matcher(source);
        assertThat(sql.find()).isTrue();
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V7__reference_items.sql"));
        assertThat(migration.replace("\r\n", "\n").strip()).isEqualTo(sql.group(1).replace("\r\n", "\n").strip());
        assertThat(migration).doesNotContain("GRANT ", "REVOKE ", "CREATE ROLE", "DO $");
        assertThat(jdbc.queryForList("SELECT version FROM learning.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).containsSubsequence("1", "2", "2.1", "3", "4", "5", "6", "7");
    }

    @Test
    void FR018_AT09_schemaAndIndexesMatchTheLockedModel() {
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='learning' "
                + "AND table_name='reference_items' ORDER BY ordinal_position", String.class)).containsExactly(
                "id", "kind", "title", "description", "url", "thumbnail_url", "hsk_min", "hsk_max", "tags",
                "author", "publisher", "published_year", "is_affiliate", "status", "sort_order", "created_by",
                "published_at", "created_at", "updated_at");
        assertColumn("id", "bigint", "NO", null);
        assertColumn("kind", "character varying", "NO", 20);
        assertColumn("title", "character varying", "NO", 200);
        assertColumn("description", "character varying", "YES", 2000);
        assertColumn("url", "text", "NO", null);
        assertColumn("thumbnail_url", "text", "YES", null);
        assertColumn("hsk_min", "smallint", "YES", null);
        assertColumn("hsk_max", "smallint", "YES", null);
        assertColumn("tags", "ARRAY", "NO", null);
        assertColumn("author", "character varying", "YES", 200);
        assertColumn("publisher", "character varying", "YES", 200);
        assertColumn("published_year", "smallint", "YES", null);
        assertColumn("is_affiliate", "boolean", "NO", null);
        assertColumn("status", "character varying", "NO", 20);
        assertColumn("sort_order", "integer", "NO", null);
        assertColumn("created_by", "bigint", "NO", null);
        for (String name : List.of("published_at", "created_at", "updated_at")) {
            assertColumn(name, "timestamp with time zone", name.equals("published_at") ? "YES" : "NO", null);
        }
        assertThat(jdbc.queryForObject("SELECT udt_name FROM information_schema.columns WHERE table_schema='learning' "
                + "AND table_name='reference_items' AND column_name='tags'", String.class)).isEqualTo("_varchar");
        assertThat(index("ix_ref_public")).contains("(kind, sort_order, id)", "WHERE", "PUBLISHED");
        assertThat(index("ix_ref_admin")).contains("(kind, status, updated_at DESC)");
    }

    @Test
    void FR018_AT09_databaseDefaultsDoNotDependOnJpa() {
        long id = insert(Map.of());
        assertThat(jdbc.queryForMap("SELECT status, sort_order, is_affiliate, cardinality(tags) AS tag_count, "
                + "published_at, author, publisher, published_year, thumbnail_url, "
                + "created_at IS NOT NULL AS created, updated_at IS NOT NULL AS updated "
                + "FROM learning.reference_items WHERE id=?", id))
                .containsEntry("status", "DRAFT").containsEntry("sort_order", 0).containsEntry("is_affiliate", false)
                .containsEntry("tag_count", 0).containsEntry("published_at", null)
                .containsEntry("author", null).containsEntry("publisher", null).containsEntry("published_year", null)
                .containsEntry("thumbnail_url", null).containsEntry("created", true).containsEntry("updated", true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"YOUTUBE_CHANNEL", "PODCAST", "BOOK"})
    void FR018_AT09_allThreeKindsCanBeDrafts(String kind) {
        long id = insert(Map.of("kind", kind));
        assertThat(jdbc.queryForObject("SELECT kind FROM learning.reference_items WHERE id=?", String.class, id))
                .isEqualTo(kind);
    }

    @ParameterizedTest
    @CsvSource({"kind,VIDEO", "status,DELETED"})
    void FR018_AT09_unknownKindAndStatusAreRejected(String column, String value) {
        reject(change(column, value), "reference_items_" + column + "_check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"kind", "title", "url", "tags", "is_affiliate", "status", "sort_order", "created_by",
            "created_at", "updated_at"})
    void FR018_AT09_requiredColumnsRejectExplicitNull(String column) {
        reject(change(column, null), column);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.test", "javascript:alert(1)", "data:text/plain,test", ""})
    void FR018_AT09_databaseRejectsNonHttpsResourceUrls(String url) {
        reject(change("url", url), "reference_items_url_check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.test/cover", "javascript:alert(1)"})
    void FR018_AT09_databaseRejectsNonHttpsThumbnails(String url) {
        reject(change("thumbnail_url", url), "reference_items_thumbnail_url_check");
    }

    @Test
    void FR018_AT09_httpsThumbnailIsAccepted() {
        long id = insert(Map.of("thumbnail_url", "https://example.test/self-authored-cover"));
        assertThat(jdbc.queryForObject("SELECT thumbnail_url FROM learning.reference_items WHERE id=?", String.class, id))
                .isEqualTo("https://example.test/self-authored-cover");
    }

    @ParameterizedTest
    @CsvSource({"hsk_min,0", "hsk_min,10", "hsk_max,0", "hsk_max,10"})
    void FR018_AT09_hskOutsideOneToNineIsRejected(String column, short value) {
        reject(change(column, value), "reference_items_" + column + "_check");
    }

    @ParameterizedTest
    @CsvSource({"1,1", "1,9", "9,9"})
    void FR018_AT09_apiStorageRangeStillAllowsOneThroughNine(short min, short max) {
        assertThat(insert(Map.of("hsk_min", min, "hsk_max", max))).isPositive();
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,NULL", "1,NULL", "NULL,9"}, nullValues = "NULL")
    void FR018_AT09_hskBoundsMayBeUnknownIndependently(Short min, Short max) {
        var values = change("hsk_min", min);
        values.put("hsk_max", max);
        assertThat(insert(values)).isPositive();
    }

    @Test
    void FR018_AT09_hskMinimumCannotExceedMaximum() {
        reject(Map.of("hsk_min", 3, "hsk_max", 2), "ck_ref_hsk");
    }

    @ParameterizedTest
    @ValueSource(ints = {1899, 2101})
    void FR018_AT09_yearOutsideTheApprovedRangeIsRejected(int year) {
        reject(Map.of("kind", "BOOK", "published_year", year), "reference_items_published_year_check");
    }

    @ParameterizedTest
    @ValueSource(ints = {1900, 2100})
    void FR018_AT09_yearBoundaryIsAccepted(int year) {
        assertThat(insert(Map.of("kind", "BOOK", "published_year", year))).isPositive();
    }

    @ParameterizedTest
    @CsvSource({"YOUTUBE_CHANNEL,author", "YOUTUBE_CHANNEL,publisher", "YOUTUBE_CHANNEL,published_year",
            "YOUTUBE_CHANNEL,is_affiliate", "PODCAST,author", "PODCAST,publisher", "PODCAST,published_year",
            "PODCAST,is_affiliate"})
    void FR018_AT09_bookFieldsAreRejectedOnBothOtherKinds(String kind, String column) {
        Object value = switch (column) {
            case "published_year" -> 2026;
            case "is_affiliate" -> true;
            default -> "Noi dung tu soan";
        };
        var values = change("kind", kind);
        values.put(column, value);
        reject(values, "ck_ref_book");
    }

    @Test
    void FR013_publishedRequiresPublicationTime() {
        reject(Map.of("status", "PUBLISHED"), "ck_ref_published");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "PUBLISHED", "HIDDEN"})
    void FR014_allThreeStatusesAllowAnExistingPublicationTime(String status) {
        assertThat(insert(Map.of("status", status, "published_at", Timestamp.from(Instant.now())))).isPositive();
    }

    @Test
    void FR018_AT09_creatorMustReferenceAnExistingUser() {
        reject(Map.of("created_by", Long.MAX_VALUE), "reference_items_created_by_fkey");
    }

    @ParameterizedTest
    @CsvSource({"title,201", "description,2001", "author,201", "publisher,201"})
    void FR018_AT09_varcharLengthsAreEnforced(String column, int length) {
        var values = change("kind", "BOOK");
        values.put(column, "x".repeat(length));
        assertThatThrownBy(() -> insert(values)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("value too long");
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 31})
    void FR018_AT09_tagElementsRespectTheThirtyCharacterLimit(int length) {
        String sql = "INSERT INTO learning.reference_items(kind,title,url,created_by,tags) "
                + "VALUES ('YOUTUBE_CHANNEL', ?, ?, ?, ARRAY[?]) RETURNING id";
        if (length == 30) {
            assertThat(jdbc.queryForObject(sql, Long.class, "Tag tu soan", "https://example.test/reference",
                    creator, "x".repeat(length))).isPositive();
        } else {
            assertThatThrownBy(() -> jdbc.queryForObject(sql, Long.class, "Tag tu soan",
                    "https://example.test/reference", creator, "x".repeat(length)))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("value too long");
        }
    }

    @Test
    void FR018_AT09_repositoryRoundTripsBookFieldsArraysAndTimestamps() {
        var item = new ReferenceItem(ReferenceItem.Kind.BOOK, "Sach tu soan", "https://example.test/book", creator);
        item.setDescription("Mo ta tu soan");
        item.setThumbnailUrl("https://example.test/cover");
        item.setHskMin((short) 1);
        item.setHskMax((short) 3);
        item.setTags(new String[] {"tu-soan", "doc-them"});
        item.setAuthor("Tac gia minh hoa");
        item.setPublisher("Nha xuat ban minh hoa");
        item.setPublishedYear((short) 2026);
        item.setAffiliate(true);
        item.setStatus(ReferenceItem.Status.PUBLISHED);
        item.setPublishedAt(Instant.parse("2026-11-13T00:00:00Z"));
        item.setSortOrder(7);
        references.save(item);
        entities.flush();
        long id = item.getId();
        entities.clear();

        var stored = references.findById(id).orElseThrow();
        assertThat(stored.getKind()).isEqualTo(ReferenceItem.Kind.BOOK);
        assertThat(stored.getTitle()).isEqualTo("Sach tu soan");
        assertThat(stored.getDescription()).isEqualTo("Mo ta tu soan");
        assertThat(stored.getUrl()).isEqualTo("https://example.test/book");
        assertThat(stored.getThumbnailUrl()).isEqualTo("https://example.test/cover");
        assertThat(stored.getHskMin()).isEqualTo((short) 1);
        assertThat(stored.getHskMax()).isEqualTo((short) 3);
        assertThat(stored.getTags()).containsExactly("tu-soan", "doc-them");
        assertThat(stored.getAuthor()).isEqualTo("Tac gia minh hoa");
        assertThat(stored.getPublisher()).isEqualTo("Nha xuat ban minh hoa");
        assertThat(stored.getPublishedYear()).isEqualTo((short) 2026);
        assertThat(stored.isAffiliate()).isTrue();
        assertThat(stored.getStatus()).isEqualTo(ReferenceItem.Status.PUBLISHED);
        assertThat(stored.getPublishedAt()).isEqualTo(Instant.parse("2026-11-13T00:00:00Z"));
        assertThat(stored.getSortOrder()).isEqualTo(7);
        assertThat(stored.getCreatedBy()).isEqualTo(creator);
        assertThat(stored.getCreatedAt()).isNotNull();
        Instant createdAt = stored.getCreatedAt();
        Instant updatedAt = stored.getUpdatedAt();
        stored.setTitle("Sach tu soan da sua");
        stored.setUrl("https://example.test/book-updated");
        references.save(stored);
        entities.flush();
        entities.clear();
        var updated = references.findById(id).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Sach tu soan da sua");
        assertThat(updated.getUrl()).isEqualTo("https://example.test/book-updated");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updatedAt);
        assertThat(references.findById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void FR014_repositoryDoesNotExposeHardDeleteOperations() {
        assertThat(org.springframework.data.repository.Repository.class.isAssignableFrom(ReferenceItemRepository.class))
                .isTrue();
        assertThat(org.springframework.data.repository.CrudRepository.class.isAssignableFrom(ReferenceItemRepository.class))
                .isFalse();
        assertThat(Arrays.stream(ReferenceItemRepository.class.getMethods()).map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.startsWith("delete") || name.startsWith("remove"));
    }

    @Test
    void FR018_AT09_upgradeFromV6AddsOnlyTheNewTableAndPreservesExistingData() {
        try (var database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("reference_upgrade")
                .withUsername("reference_owner").withPassword(UUID.randomUUID().toString())) {
            database.start();
            flyway(database, "6").migrate();
            var upgrade = new JdbcTemplate(new DriverManagerDataSource(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            List<String> tables = tables(upgrade);
            List<String> columns = existingColumns(upgrade);
            long legacy = upgrade.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi) "
                    + "VALUES ('WORD', ?, ?) RETURNING id", Long.class, "自造", "du lieu cu tu soan");
            var migration = flyway(database, "7");
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(migration.validateWithResult().validationSuccessful).isTrue();
            assertThat(migration.migrate().migrationsExecuted).isZero();
            assertThat(tables(upgrade)).containsAll(tables).hasSize(tables.size() + 1)
                    .contains("learning.reference_items");
            assertThat(existingColumns(upgrade)).isEqualTo(columns);
            assertThat(upgrade.queryForObject("SELECT meaning_vi FROM learning.lexemes WHERE id=?", String.class, legacy))
                    .isEqualTo("du lieu cu tu soan");
        }
    }

    private long insert(Map<String, Object> changes) {
        assertThat(INSERT_COLUMNS).containsAll(changes.keySet());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("kind", "YOUTUBE_CHANNEL");
        values.put("title", "Tai lieu tu soan");
        values.put("url", "https://example.test/reference");
        values.put("created_by", creator);
        values.putAll(changes);
        String sql = "INSERT INTO learning.reference_items (" + String.join(",", values.keySet()) + ") VALUES ("
                + String.join(",", java.util.Collections.nCopies(values.size(), "?")) + ") RETURNING id";
        return jdbc.queryForObject(sql, Long.class, values.values().toArray());
    }

    private Map<String, Object> change(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private void reject(Map<String, Object> values, String constraint) {
        assertThatThrownBy(() -> insert(values)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    private void assertColumn(String column, String type, String nullable, Integer length) {
        assertThat(jdbc.queryForMap("SELECT data_type,is_nullable,character_maximum_length FROM information_schema.columns "
                + "WHERE table_schema='learning' AND table_name='reference_items' AND column_name=?", column))
                .containsEntry("data_type", type).containsEntry("is_nullable", nullable)
                .containsEntry("character_maximum_length", length);
    }

    private String index(String name) {
        return jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname='learning' AND indexname=?",
                String.class, name);
    }

    private Flyway flyway(PostgreSQLContainer<?> database, String target) {
        return Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .schemas("learning", "auth", "community", "shared").defaultSchema("learning").target(target).load();
    }

    private List<String> tables(JdbcTemplate database) {
        return database.queryForList("SELECT table_schema||'.'||table_name FROM information_schema.tables "
                + "WHERE table_schema IN ('learning','auth','community','shared') AND table_type='BASE TABLE' "
                + "AND table_name <> 'flyway_schema_history' ORDER BY table_schema,table_name", String.class);
    }

    private List<String> existingColumns(JdbcTemplate database) {
        return database.queryForList("SELECT table_schema||'.'||table_name||'.'||column_name||':'||data_type||':'||is_nullable "
                + "FROM information_schema.columns WHERE table_schema IN ('learning','auth','community','shared') "
                + "AND table_name NOT IN ('reference_items','flyway_schema_history') "
                + "ORDER BY table_schema,table_name,ordinal_position", String.class);
    }
}