package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.learning.api.ContestAttemptApi;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@Transactional
class PublicContestIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/public/community/contests";
    private static final Instant NOW = Instant.parse("2035-11-14T04:00:00Z");
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final List<String> SUMMARY_FIELDS = List.of("id", "name", "startsAt", "endsAt", "state", "prizes",
            "maxParticipants", "participantCount", "rankingPublished");
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private ContestRepository repository;
    @Autowired private EntityManager entities;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private ContestAttemptApi attempts;
    private long creator;
    private long exam;
    private Instant now;

    @BeforeEach
    void fixture() {
        now = NOW;
        when(clock.instant()).thenAnswer(ignored -> now);
        when(clock.millis()).thenAnswer(ignored -> now.toEpochMilli());
        when(clock.withZone(any(ZoneId.class))).thenAnswer(call -> Clock.fixed(now, call.getArgument(0)));
        creator = user();
        exam = jdbc.queryForObject("INSERT INTO learning.exams(code,title,hsk_level,status,sections) "
                        + "VALUES (?,'Đề cuộc thi tự soạn',1,'DRAFT',?::jsonb) RETURNING id", Long.class,
                "PUBLIC-" + UUID.randomUUID(),
                "[{\"index\":0,\"name\":\"Đọc\",\"duration_min\":10},{\"index\":1,\"name\":\"Đọc thêm\",\"duration_min\":5}]");
        clearInvocations(attempts);
    }

    @Test
    void FR009_guestGetsAnEmptyPageWithContractDefaults() throws Exception {
        JsonNode page = ok(get(BASE));
        assertThat(fields(page)).containsExactlyInAnyOrder("items", "page", "size", "totalElements");
        assertThat(page.path("items")).isEmpty();
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(20);
        assertThat(page.path("totalElements").asLong()).isZero();
        verifyNoInteractions(attempts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "CANCELLED", "CLOSED"})
    void AT14_nonPublishedContestsAreAbsentAndDetailIsNotFound(String state) throws Exception {
        long id = contest(state, -60, 60);
        assertThat(ok(get(BASE)).path("items")).isEmpty();
        error(get(BASE + "/{id}", id), 404, "CONTEST_NOT_FOUND");
        verifyNoInteractions(attempts);
    }

    @Test
    void FR009_groupsAndOrdersAllPublishedContestsBeforePaginationWithStableTies() throws Exception {
        long endedOld = contest("PUBLISHED", -300, -200);
        long upcomingLate = contest("PUBLISHED", 200, 300);
        long ongoingLate = contest("PUBLISHED", -30, 120);
        long endedRecent = contest("PUBLISHED", -200, -100);
        long upcomingEarly = contest("PUBLISHED", 100, 200);
        long ongoingEarly = contest("PUBLISHED", -120, 60);
        long ongoingTie = contest("PUBLISHED", -60, 60);
        contest("DRAFT", -60, 60);
        contest("CANCELLED", 100, 200);
        List<Long> ordered = List.of(ongoingEarly, ongoingTie, ongoingLate, upcomingEarly, upcomingLate, endedRecent, endedOld);
        JsonNode page = ok(get(BASE));
        assertThat(ids(page)).containsExactlyElementsOf(ordered);
        assertThat(page.path("totalElements").asLong()).isEqualTo(7);
        verify(attempts, never()).exam(anyLong());
        for (long id : ordered) verify(attempts).countAttempts(id);
    }

    @Test
    void FR009_pagesUseTheSameGlobalOrderAndTotalAndDoNotFetchOffPageCounts() throws Exception {
        long ended = contest("PUBLISHED", -180, -60);
        long upcoming = contest("PUBLISHED", 60, 120);
        long ongoing = contest("PUBLISHED", -60, 60);
        JsonNode first = ok(get(BASE).param("size", "2"));
        JsonNode second = ok(get(BASE).param("page", "1").param("size", "2"));
        assertThat(ids(first)).containsExactly(ongoing, upcoming);
        assertThat(ids(second)).containsExactly(ended);
        assertThat(first.path("totalElements").asLong()).isEqualTo(3);
        assertThat(second.path("totalElements").asLong()).isEqualTo(3);
        assertThat(second.path("page").asInt()).isEqualTo(1);
        assertThat(second.path("size").asInt()).isEqualTo(2);
        verify(attempts, times(1)).countAttempts(ended);
        verify(attempts, times(1)).countAttempts(ongoing);
        verify(attempts, times(1)).countAttempts(upcoming);
        clearInvocations(attempts);
        JsonNode beyond = ok(get(BASE).param("page", Integer.toString(Integer.MAX_VALUE)).param("size", "50"));
        assertThat(beyond.path("items")).isEmpty();
        assertThat(beyond.path("totalElements").asLong()).isEqualTo(3);
        verifyNoInteractions(attempts);
    }

    @Test
    void FR009_defaultPageIsTwentyAndMaximumSizeIsFifty() throws Exception {
        for (int index = 0; index < 21; index++) contest("PUBLISHED", index + 60, index + 120);
        assertThat(ok(get(BASE)).path("items")).hasSize(20);
        assertThat(ok(get(BASE).param("size", "50")).path("items")).hasSize(21);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPCOMING", "ONGOING", "ENDED"})
    void AT14_stateFilterHasItsOwnTotalAndRetainsOnlyPublishedMatches(String state) throws Exception {
        long upcoming = contest("PUBLISHED", 60, 120);
        long ongoing = contest("PUBLISHED", -60, 60);
        long ended = contest("PUBLISHED", -120, -60);
        contest("DRAFT", -60, 60);
        long expected = switch (state) {
            case "UPCOMING" -> upcoming;
            case "ONGOING" -> ongoing;
            default -> ended;
        };
        JsonNode page = ok(get(BASE).param("state", state));
        assertThat(ids(page)).containsExactly(expected);
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("state").asText()).isEqualTo(state);
        verify(attempts).countAttempts(expected);
    }

    @ParameterizedTest
    @CsvSource({"-1,UPCOMING", "0,ONGOING", "59999999,ONGOING", "60000000,ENDED"})
    void AT14_listDetailAndFilterAgreeImmediatelyBeforeAndAtBothTimeBoundaries(long micros, String state) throws Exception {
        long id = contest("PUBLISHED", 0, 60);
        now = NOW.plusNanos(micros * 1000);
        assertThat(ok(get(BASE + "/{id}", id)).path("state").asText()).isEqualTo(state);
        assertThat(ids(ok(get(BASE).param("state", state)))).containsExactly(id);
        for (String other : List.of("UPCOMING", "ONGOING", "ENDED")) {
            if (!other.equals(state)) assertThat(ok(get(BASE).param("state", other)).path("items")).isEmpty();
        }
    }

    @Test
    void FR010_detailUsesTheRealLearningPortAndReturnsOnlyPublicFieldsWithVietnamOffsets() throws Exception {
        question("APPROVED", 0);
        question("APPROVED", 1);
        question("PENDING_REVIEW", 2);
        long id = contest("PUBLISHED", -60, 60);
        jdbc.update("UPDATE community.contests SET description=?,max_participants=200,prizes=?::jsonb WHERE id=?",
                "Mô tả cuộc thi tự soạn", "[{\"fromRank\":1,\"toRank\":2,\"description\":\"Bộ sách mẫu\"}]", id);
        JsonNode detail = ok(get(BASE + "/{id}", id));
        List<String> expected = new ArrayList<>(SUMMARY_FIELDS);
        expected.addAll(List.of("description", "examTitle", "durationMinutes", "questionCount"));
        assertThat(fields(detail)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(detail.path("name").asText()).isEqualTo("Cuộc thi tự soạn");
        assertThat(detail.path("description").asText()).isEqualTo("Mô tả cuộc thi tự soạn");
        assertThat(detail.path("examTitle").asText()).isEqualTo("Đề cuộc thi tự soạn");
        assertThat(detail.path("durationMinutes").asInt()).isEqualTo(15);
        assertThat(detail.path("questionCount").asInt()).isEqualTo(2);
        assertThat(detail.path("maxParticipants").asInt()).isEqualTo(200);
        assertThat(detail.path("participantCount").asLong()).isZero();
        assertThat(detail.path("rankingPublished").asBoolean()).isFalse();
        assertThat(detail.path("prizes")).hasSize(1);
        JsonNode prize = detail.path("prizes").get(0);
        assertThat(fields(prize)).containsExactlyInAnyOrder("fromRank", "toRank", "description");
        assertThat(prize.path("fromRank").asInt()).isEqualTo(1);
        assertThat(prize.path("toRank").asInt()).isEqualTo(2);
        assertThat(prize.path("description").asText()).isEqualTo("Bộ sách mẫu");
        assertVietnamTime(detail, "startsAt", NOW.minusSeconds(60));
        assertVietnamTime(detail, "endsAt", NOW.plusSeconds(60));
        JsonNode summary = ok(get(BASE)).path("items").get(0);
        assertThat(fields(summary)).containsExactlyInAnyOrderElementsOf(SUMMARY_FIELDS);
        for (String field : SUMMARY_FIELDS) assertThat(summary.path(field)).isEqualTo(detail.path(field));
        verify(attempts).exam(exam);
        verify(attempts, times(2)).countAttempts(id);
        Contest stored = repository.findPublished(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("PUBLISHED");
        assertThat(stored.getCreatedBy()).isEqualTo(creator);
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    @Test
    void FR010_nullableFieldsEmptyPrizesAndZeroDurationRemainInTheResponse() throws Exception {
        jdbc.update("UPDATE learning.exams SET sections='[]'::jsonb WHERE id=?", exam);
        long id = contest("PUBLISHED", -60, 60);
        JsonNode detail = ok(get(BASE + "/{id}", id));
        assertThat(detail.has("description")).isTrue();
        assertThat(detail.path("description").isNull()).isTrue();
        assertThat(detail.has("maxParticipants")).isTrue();
        assertThat(detail.path("maxParticipants").isNull()).isTrue();
        assertThat(detail.path("prizes")).isEmpty();
        assertThat(detail.path("durationMinutes").asInt()).isZero();
        assertThat(detail.path("questionCount").asInt()).isZero();
    }

    @Test
    void FR010_vietnamDateTimesPreserveDatabaseMicrosecondPrecision() throws Exception {
        long id = contest("PUBLISHED", -60, 60);
        Instant start = NOW.minusSeconds(60).plusNanos(123_456_000);
        Instant end = NOW.plusSeconds(60).plusNanos(654_321_000);
        jdbc.update("UPDATE community.contests SET starts_at=?,ends_at=? WHERE id=?", Timestamp.from(start), Timestamp.from(end), id);
        JsonNode detail = ok(get(BASE + "/{id}", id));
        assertVietnamTime(detail, "startsAt", start);
        assertVietnamTime(detail, "endsAt", end);
    }

    @Test
    void FR009_participantCountIncludesEveryContestAttemptStatusAndIsRefreshedWithoutLeakingResults() throws Exception {
        long id = contest("PUBLISHED", -120, -60);
        long other = contest("PUBLISHED", -180, -120);
        for (String state : List.of("IN_PROGRESS", "SUBMITTED", "EXPIRED", "ABANDONED")) attempt(id, "CONTEST", state);
        attempt(other, "CONTEST", "SUBMITTED");
        attempt(id, "EXAM", "SUBMITTED");
        JsonNode detail = ok(get(BASE + "/{id}", id));
        assertThat(detail.path("participantCount").asLong()).isEqualTo(4);
        assertThat(detail.path("rankingPublished").asBoolean()).isFalse();
        attempt(id, "CONTEST", "SUBMITTED");
        jdbc.update("UPDATE community.contests SET ranking_published_at=? WHERE id=?", Timestamp.from(NOW), id);
        entities.clear();
        JsonNode updated = ok(get(BASE + "/{id}", id));
        assertThat(updated.path("participantCount").asLong()).isEqualTo(5);
        assertThat(updated.path("rankingPublished").asBoolean()).isTrue();
        assertThat(detail.toString()).doesNotContain("email", "userId", "score", "answer", "accept", "correct", "createdBy", "examId");
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,abc", "page,2147483648", "size,0", "size,-1", "size,51", "size,abc",
            "state,CLOSED", "state,DRAFT", "state,ongoing", "state,UNKNOWN"})
    void FR009_invalidQueriesReturnTheExistingValidationEnvelope(String parameter, String value) throws Exception {
        JsonNode body = error(get(BASE).param(parameter, value), 422, "VALIDATION_FAILED");
        assertThat(body.path("details")).isNotEmpty();
        verifyNoInteractions(attempts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "9223372036854775807", "9223372036854775808", "invalid"})
    void FR010_missingAndInvalidIdsUseTheSameNotFoundEnvelope(String id) throws Exception {
        error(get(BASE + "/" + id), 404, "CONTEST_NOT_FOUND");
        verifyNoInteractions(attempts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "TEACHER", "MODERATOR", "CONTENT_EDITOR", "ACCOUNTANT", "ADMIN"})
    void FR009_publicReadsAlsoAllowEveryAuthenticatedRole(String role) throws Exception {
        long id = contest("PUBLISHED", -60, 60);
        String token = jwt.issueAccessToken(creator, List.of(role), false, UUID.randomUUID());
        assertThat(ids(ok(get(BASE).header("Authorization", "Bearer " + token)))).containsExactly(id);
        assertThat(ok(get(BASE + "/{id}", id).header("Authorization", "Bearer " + token)).path("id").asLong()).isEqualTo(id);
    }

    @Test
    void FR009_cookieReadsNeedNoCsrfAndDoNotOpenCommunityWrites() throws Exception {
        long id = contest("PUBLISHED", -60, 60);
        Cookie cookie = new Cookie("access_token", jwt.issueAccessToken(creator, List.of("LEARNER"), false, UUID.randomUUID()));
        assertThat(ids(ok(get(BASE).cookie(cookie)))).containsExactly(id);
        assertThat(ok(get(BASE + "/{id}", id).cookie(cookie)).path("id").asLong()).isEqualTo(id);
        error(post("/api/community/contests/{id}/attempts", id).cookie(cookie), 403, "CSRF_TOKEN_INVALID");
        error(post("/api/community/contests/{id}/attempts", id), 401, "UNAUTHENTICATED");
    }

    @Test
    void FR009_FR010_generatedPublicOperationsMatchTheApprovedPathsMethodsAndStatuses() throws Exception {
        JsonNode document = ok(get("/v3/api-docs"));
        JsonNode source = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(
                Path.of("specs", "021-contests", "contracts", "openapi-contests.yaml")));
        for (String path : List.of(BASE, BASE + "/{id}")) {
            JsonNode generated = document.path("paths").path(path);
            assertThat(fields(generated)).containsExactly("get");
            JsonNode actual = generated.path("get");
            JsonNode expected = source.path("paths").path(path).path("get");
            assertThat(actual.path("operationId")).isEqualTo(expected.path("operationId"));
            assertThat(fields(actual.path("responses"))).containsExactlyInAnyOrderElementsOf(fields(expected.path("responses")));
            assertThat(actual.path("security")).isEmpty();
            assertThat(actual.path("responses").path("200").path("content").path("application/json").path("schema").path("$ref"))
                    .isEqualTo(expected.path("responses").path("200").path("content").path("application/json").path("schema").path("$ref"));
        }
        Set<String> publicPaths = new HashSet<>();
        document.path("paths").fieldNames().forEachRemaining(path -> {
            if (path.equals(BASE) || path.startsWith(BASE + "/")) publicPaths.add(path);
        });
        assertThat(publicPaths).containsExactlyInAnyOrder(BASE, BASE + "/{id}");
    }

    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode error(MockHttpServletRequestBuilder request, int statusCode, String code) throws Exception {
        JsonNode body = mapper.readTree(mvc.perform(request).andExpect(status().is(statusCode))
                .andReturn().getResponse().getContentAsString());
        assertThat(body.path("error_code").asText()).isEqualTo(code);
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.path("request_id").asText()).isNotBlank();
        assertThat(body.has("trace")).isFalse();
        assertThat(body.has("exception")).isFalse();
        return body;
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.path("items").forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    private static void assertVietnamTime(JsonNode node, String field, Instant expected) {
        String text = node.path(field).asText();
        assertThat(text).endsWith("+07:00");
        OffsetDateTime value = OffsetDateTime.parse(text);
        assertThat(value.getOffset()).isEqualTo(ZoneOffset.ofHours(7));
        assertThat(value.toInstant()).isEqualTo(expected);
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Người học thử') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", HASH);
    }

    private long contest(String status, long startSeconds, long endSeconds) {
        return jdbc.queryForObject("INSERT INTO community.contests(name,exam_id,starts_at,ends_at,status,created_by) "
                        + "VALUES ('Cuộc thi tự soạn',?,?,?,?,?) RETURNING id", Long.class, exam,
                Timestamp.from(NOW.plusSeconds(startSeconds)), Timestamp.from(NOW.plusSeconds(endSeconds)), status, creator);
    }

    private void question(String status, int order) {
        long id = jdbc.queryForObject("INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,status) "
                        + "VALUES (?,'MULTIPLE_CHOICE',1,'水 có nghĩa gì?',?::jsonb,'A',?) RETURNING id", Long.class,
                "PUBLIC-" + UUID.randomUUID(), "[{\"key\":\"A\",\"text\":\"nước\",\"is_correct\":true},"
                        + "{\"key\":\"B\",\"text\":\"lửa\",\"is_correct\":false}]", status);
        jdbc.update("INSERT INTO learning.exam_questions(exam_id,question_id,order_index) VALUES (?,?,?)", exam, id, order);
    }

    private void attempt(long contest, String kind, String status) {
        jdbc.update("INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,status,suspicious,submitted_at) VALUES (?,?,?,?,?,true,?)",
                user(), kind, kind.equals("CONTEST") ? "CONTEST" : "MOCK_EXAM", contest, status,
                status.equals("SUBMITTED") ? Timestamp.from(NOW.minusSeconds(60)) : null);
    }
}
