package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ContestResultsControllerIntegrationTest extends ContestFixtures {
    @Test
    void AT08_beforePublicationHasNullPrivateValuesIncludingInTheRawJson() throws Exception {
        attempt(learner, contest, "SUBMITTED", 9_876_543L, 9_999_999L, 7_654_321, true);
        attempt(creator, contest, "SUBMITTED", 8_765_432L, 9_999_999L, 6_543_210, false);
        var result = response(results(), 200);
        assertThat(fields(result)).containsExactlyInAnyOrder("contest", "published", "mine", "ranking");
        assertThat(result.path("published").asBoolean()).isFalse();
        assertThat(result.path("ranking").isNull()).isTrue();
        var mine = result.path("mine");
        assertThat(fields(mine)).containsExactlyInAnyOrder("status", "submittedAt", "score", "maxScore", "durationMs", "rank", "excluded");
        assertThat(mine.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(time(mine, "submittedAt")).isEqualTo(NOW.minusSeconds(5));
        for (String field : List.of("score", "maxScore", "durationMs", "rank", "excluded")) assertThat(mine.path(field).isNull()).as(field).isTrue();
        assertThat(result.toString()).doesNotContain("9876543", "9999999", "7654321", "8765432", "6543210");
        noPrivateFields(result);
        verify(attempts, never()).ranking(anyLong(), anyInt(), anyInt());
        verify(attempts, never()).rankOf(anyLong(), anyLong());
        verify(auth, never()).publicProfiles(anySet());
    }

    @Test
    void FR014_noAttemptProducesNullMineAndAnEmptyRankingOnlyAfterPublication() throws Exception {
        var hidden = response(results(), 200);
        assertThat(hidden.path("mine").isNull()).isTrue();
        assertThat(hidden.path("ranking").isNull()).isTrue();
        publishResults();
        var published = response(results(), 200);
        assertThat(published.path("published").asBoolean()).isTrue();
        assertThat(published.path("mine").isNull()).isTrue();
        assertThat(published.at("/ranking/items")).isEmpty();
        assertThat(published.at("/ranking/page").asInt()).isZero();
        assertThat(published.at("/ranking/size").asInt()).isEqualTo(20);
        assertThat(published.at("/ranking/totalElements").asLong()).isZero();
        verify(auth, never()).publicProfiles(anySet());
    }

    @Test
    void AT11_publishedRankingUsesServerOrderAndOneNameBatchWithoutEmailAvatarOrAnswers() throws Exception {
        long fast = user(true);
        long tied = user(true);
        long low = user(true);
        long excluded = user(true);
        attempt(learner, contest, "SUBMITTED", 10L, 20L, 4000, false);
        attempt(fast, contest, "SUBMITTED", 10L, 20L, 1000, false);
        attempt(tied, contest, "SUBMITTED", 10L, 20L, 1000, false);
        attempt(low, contest, "SUBMITTED", 9L, 20L, 20, false);
        attempt(excluded, contest, "SUBMITTED", 20L, 20L, 10, true);
        jdbc.update("UPDATE auth.users SET display_name='Người nhanh' WHERE id=?", fast);
        jdbc.update("UPDATE auth.users SET display_name='Người đồng hạng' WHERE id=?", tied);
        publishResults();
        var result = response(results(), 200);
        var rows = result.at("/ranking/items");
        assertThat(ids(rows)).containsExactly(fast, tied, learner, low);
        assertThat(result.at("/ranking/totalElements").asLong()).isEqualTo(4);
        for (int index = 0; index < rows.size(); index++) {
            assertThat(rows.get(index).path("rank").asLong()).isEqualTo(index + 1L);
            assertThat(fields(rows.get(index))).containsExactlyInAnyOrder("rank", "userId", "displayName", "score", "maxScore", "durationMs");
        }
        assertThat(rows.get(0).path("displayName").asText()).isEqualTo("Người nhanh");
        assertThat(rows.get(1).path("displayName").asText()).isEqualTo("Người đồng hạng");
        assertThat(result.at("/mine/score").asLong()).isEqualTo(10);
        assertThat(result.at("/mine/maxScore").asLong()).isEqualTo(20);
        assertThat(result.at("/mine/durationMs").asInt()).isEqualTo(4000);
        assertThat(result.at("/mine/rank").asLong()).isEqualTo(3);
        assertThat(result.at("/mine/excluded").asBoolean()).isFalse();
        noPrivateFields(result);
        assertThat(result.toString()).doesNotContain("@example.test", "https://example.test/avatar.png");
        verify(auth, times(1)).publicProfiles(Set.of(fast, tied, learner, low));
    }

    @Test
    void FR014_missingPublicProfileUsesNullDisplayNameWithoutDroppingTheRankingRow() throws Exception {
        attempt(learner, contest, "SUBMITTED", 10L, 20L, 1000, false);
        publishResults();
        doReturn(Map.of()).when(auth).publicProfiles(Set.of(learner));
        var result = response(results(), 200);
        var rows = result.at("/ranking/items");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).path("userId").asLong()).isEqualTo(learner);
        assertThat(rows.get(0).has("displayName")).isTrue();
        assertThat(rows.get(0).path("displayName").isNull()).isTrue();
        assertThat(rows.get(0).path("score").asLong()).isEqualTo(10);
        noPrivateFields(result);
        verify(auth, times(1)).publicProfiles(Set.of(learner));
    }

    @Test
    void FR014_excludedOwnAttemptKeepsScoreButHasNoRankAndDoesNotEnterRanking() throws Exception {
        attempt(learner, contest, "SUBMITTED", 10L, 20L, null, true);
        publishResults();
        var result = response(results(), 200);
        assertThat(result.at("/mine/excluded").asBoolean()).isTrue();
        assertThat(result.at("/mine/score").asLong()).isEqualTo(10);
        assertThat(result.at("/mine/rank").isNull()).isTrue();
        assertThat(result.at("/mine/durationMs").isNull()).isTrue();
        assertThat(result.at("/ranking/items")).isEmpty();
    }

    @Test
    void FR014_identityComesFromJwtAndPageRanksRemainGlobal() throws Exception {
        List<Long> ranked = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            long id = user(true); ranked.add(id);
            attempt(id, contest, "SUBMITTED", 100L - index, 100L, 1000, false);
        }
        publishResults();
        var result = response(results().param("userId", Long.toString(ranked.get(0))).param("page", "1").param("size", "2"), 200);
        assertThat(result.path("mine").isNull()).isTrue();
        assertThat(ids(result.at("/ranking/items"))).containsExactly(ranked.get(2), ranked.get(3));
        assertThat(result.at("/ranking/items/0/rank").asLong()).isEqualTo(3);
        assertThat(result.at("/ranking/totalElements").asLong()).isEqualTo(5);
        var beyond = response(results().param("page", "2147483647").param("size", "50"), 200);
        assertThat(beyond.at("/ranking/items")).isEmpty();
        assertThat(beyond.at("/ranking/totalElements").asLong()).isEqualTo(5);
        verify(auth).publicProfiles(Set.of(ranked.get(2), ranked.get(3)));
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,abc", "page,2147483648", "size,0", "size,-1", "size,51", "size,abc"})
    void FR014_invalidPaginationUsesTheApproved422(String parameter, String value) throws Exception {
        error(results().param(parameter, value), 422, "VALIDATION_FAILED");
        verify(attempts, never()).ranking(anyLong(), anyInt(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "CANCELLED", "CLOSED"})
    void FR014_nonPublishedContestResultsAreNotFound(String state) throws Exception {
        jdbc.update("UPDATE community.contests SET status=? WHERE id=?", state, contest);
        error(results(), 404, "CONTEST_NOT_FOUND");
    }

    @Test
    void FR014_generatedOperationsMatchAllFourApprovedPathsMethodsStatusesAndSecurity() throws Exception {
        var document = response(get("/v3/api-docs"), 200);
        var source = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(
                Path.of("specs", "021-contests", "contracts", "openapi-contests.yaml")));
        Set<String> seen = new HashSet<>();
        for (String suffix : List.of("/attempts", "/attempts/submit", "/attempts/events", "/results")) {
            String path = BASE + "/{id}" + suffix;
            String method = suffix.equals("/results") ? "get" : "post";
            var item = document.at("/paths").path(path);
            assertThat(fields(item)).containsExactly(method);
            var actual = item.path(method);
            var expected = source.path("paths").path(path).path(method);
            assertThat(actual.path("operationId")).isEqualTo(expected.path("operationId"));
            assertThat(fields(actual.path("responses"))).containsExactlyInAnyOrderElementsOf(fields(expected.path("responses")));
            Set<Set<String>> security = new HashSet<>();
            actual.path("security").forEach(alternative -> security.add(fields(alternative)));
            assertThat(security).containsExactlyInAnyOrder(Set.of("cookieAuth"), Set.of("bearerAuth"));
            seen.add(method + " " + path);
            for (String code : List.of("200", "201")) {
                if (!expected.path("responses").has(code)) continue;
                assertThat(actual.path("responses").path(code).at("/content/application~1json/schema/$ref"))
                        .isEqualTo(expected.path("responses").path(code).at("/content/application~1json/schema/$ref"));
            }
        }
        Set<String> actualPaths = new HashSet<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (path.getKey().startsWith(BASE + "/")) path.getValue().fieldNames().forEachRemaining(method -> actualPaths.add(method + " " + path.getKey()));
        });
        assertThat(actualPaths).containsExactlyInAnyOrderElementsOf(seen);
    }

    @Test
    void NFR_P02_oneThousandContestAttemptsMeetTheRankingBudgetAndUseOneNameBatchPerPage() throws Exception {
        for (int index = 0; index < 1000; index++) attempt(user(true), contest, "SUBMITTED", (long) index, 1000L, 1000, false);
        publishResults();
        for (int index = 0; index < 10; index++) response(results(), 200);
        List<Long> samples = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            long start = System.nanoTime();
            var result = response(results(), 200);
            samples.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            assertThat(result.at("/ranking/items")).hasSize(20);
            assertThat(result.at("/ranking/totalElements").asLong()).isEqualTo(1000);
        }
        Collections.sort(samples);
        long p95 = samples.get(28);
        System.out.println("CONTEST_RESULTS_NFR_P02 samples=30 p50Ms=" + samples.get(14) + " p95Ms=" + p95);
        assertThat(p95).isLessThan(300);
        verify(auth, times(40)).publicProfiles(argThat(ids -> ids.size() == 20));
    }

    private MockHttpServletRequestBuilder results() {
        return get(BASE + "/{id}/results", contest).header("Authorization", bearer(learner));
    }

    private static List<Long> ids(JsonNode rows) {
        List<Long> ids = new ArrayList<>(); rows.forEach(row -> ids.add(row.path("userId").asLong())); return ids;
    }
}
