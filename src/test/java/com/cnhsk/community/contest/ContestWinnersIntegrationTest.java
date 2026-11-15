package com.cnhsk.community.contest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.ToolProvider;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.shared.audit.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ExtendWith(OutputCaptureExtension.class)
class ContestWinnersIntegrationTest extends ContestFixtures {
    private static final String ADMIN = "/api/admin/contests";
    @MockitoSpyBean AuditService audit;

    @Test
    void AT18_onlyWinningRanksExposeContactEmailsAndEveryReadIsAudited(CapturedOutput output) throws Exception {
        long second = user(true);
        long nonWinner = user(true);
        attempt(learner, contest, "SUBMITTED", 9L, 10L, 2000, false);
        attempt(second, contest, "SUBMITTED", 8L, 10L, 3000, false);
        attempt(nonWinner, contest, "SUBMITTED", 7L, 10L, 4000, false);
        prizes("[{\"fromRank\":1,\"toRank\":2,\"description\":\"Giải tự soạn\"}]");
        publishResults();
        clearInvocations(auth, attempts);

        var http = mvc.perform(winners()).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        JsonNode rows = mapper.readTree(http.getContentAsString(StandardCharsets.UTF_8));
        assertThat(rows).hasSize(2);
        for (int index = 0; index < 2; index++) {
            JsonNode row = rows.get(index);
            long userId = index == 0 ? learner : second;
            assertThat(fields(row)).containsExactlyInAnyOrder("rank", "userId", "displayName", "email", "score", "durationMs", "prize");
            assertThat(row.path("rank").asLong()).isEqualTo(index + 1);
            assertThat(row.path("userId").asLong()).isEqualTo(userId);
            assertThat(row.path("email").asText()).isEqualTo(email(userId));
            assertThat(row.path("score").asLong()).isEqualTo(9 - index);
            assertThat(row.path("prize").asText()).isEqualTo("Giải tự soạn");
        }
        verify(auth, times(1)).publicProfiles(Set.of(learner, second));
        verify(auth, times(1)).contactEmails(Set.of(learner, second));
        assertThat(rows.toString()).doesNotContain(email(nonWinner), email(creator));
        assertThat(audits()).isEqualTo(1);
        response(winners(), 200);
        assertThat(audits()).isEqualTo(2);
        List<String> details = jdbc.queryForList("SELECT detail::text FROM shared.audit_logs WHERE action='CONTEST_WINNERS_VIEW' "
                + "AND target_type='CONTEST' AND target_id=? AND actor_id=?", String.class, contest, creator);
        for (String detail : details) {
            assertThat(mapper.readTree(detail).path("winnerCount").asInt()).isEqualTo(2);
            assertThat(detail).doesNotContain(email(learner), email(second), "email");
        }
        assertThat(output.getAll()).doesNotContain(email(learner), email(second));
    }

    @Test
    void AT18_rangesSpanRankingPagesAndOnlyAwardedIdsReachAuth() throws Exception {
        List<Long> ranked = new ArrayList<>();
        for (int rank = 1; rank <= 102; rank++) {
            long userId = user(true);
            ranked.add(userId);
            attempt(userId, contest, "SUBMITTED", 1000L - rank, 1000L, 2000, false);
        }
        prizes("[{\"fromRank\":50,\"toRank\":51,\"description\":\"Giải A\"},"
                + "{\"fromRank\":101,\"toRank\":101,\"description\":\"Giải B\"}]");
        publishResults();
        clearInvocations(auth, attempts);
        JsonNode rows = response(winners(), 200);
        assertThat(rows.findValuesAsText("rank")).containsExactly("50", "51", "101");
        Set<Long> expected = Set.of(ranked.get(49), ranked.get(50), ranked.get(100));
        verify(auth, times(1)).publicProfiles(expected);
        verify(auth, times(1)).contactEmails(expected);
        for (int page = 0; page < 3; page++) verify(attempts, times(1)).ranking(contest, page, 50);
        verify(attempts, never()).ranking(contest, 3, 50);
    }

    @Test
    void AT18_overlapsUseFirstJsonPrizeAndReversedRangesDoNotExtendTheRead() throws Exception {
        attempt(learner, contest, "SUBMITTED", 2L, 2L, 2000, false);
        long second = user(true);
        attempt(second, contest, "SUBMITTED", 1L, 2L, 3000, false);
        prizes("[{\"fromRank\":1,\"toRank\":2,\"description\":\"Giải đầu\"},"
                + "{\"fromRank\":1,\"toRank\":1,\"description\":\"Giải sau\"},"
                + "{\"fromRank\":1000,\"toRank\":999,\"description\":\"Khoảng đảo\"}]");
        publishResults();
        clearInvocations(attempts);
        JsonNode rows = response(winners(), 200);
        assertThat(rows).hasSize(2);
        assertThat(rows.findValuesAsText("prize")).containsExactly("Giải đầu", "Giải đầu");
        verify(attempts, times(1)).ranking(contest, 0, 50);
        verify(attempts, never()).ranking(contest, 1, 50);
    }

    @Test
    void AT18_stopsAtTheEndOfRankingEvenWhenPrizeRangeIsHuge() throws Exception {
        attempt(learner, contest, "SUBMITTED", 1L, 1L, null, false);
        prizes("[{\"fromRank\":1,\"toRank\":2147483647,\"description\":\"Giải tự soạn\"}]");
        publishResults();
        clearInvocations(attempts);
        JsonNode rows = response(winners(), 200);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).has("durationMs")).isTrue();
        assertThat(rows.get(0).path("durationMs").isNull()).isTrue();
        verify(attempts, times(1)).ranking(contest, 0, 50);
        verify(attempts, never()).ranking(contest, 1, 50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[{\"fromRank\":3,\"toRank\":2,\"description\":\"Đảo\"}]",
            "[{\"fromRank\":9,\"toRank\":10,\"description\":\"Không có người\"}]"})
    void AT18_emptyWinnerListsAreStillAuditedWithoutEmailLookups(String prizes) throws Exception {
        prizes(prizes);
        publishResults();
        clearInvocations(auth);
        assertThat(response(winners(), 200)).isEmpty();
        assertThat(audits()).isEqualTo(1);
        verify(auth, never()).contactEmails(anySet());
        verify(auth, never()).publicProfiles(anySet());
    }

    @Test
    void AT18_ineligibleAndOtherContestAttemptsCannotWin() throws Exception {
        long suspicious = user(true);
        long incomplete = user(true);
        long running = user(true);
        long otherUser = user(true);
        long otherContest = contest(-60, 3600, null);
        attempt(suspicious, contest, "SUBMITTED", 10L, 10L, 2000, true);
        attempt(incomplete, contest, "SUBMITTED", 9L, null, 2000, false);
        attempt(running, contest, "IN_PROGRESS", null, null, null, false);
        attempt(otherUser, otherContest, "SUBMITTED", 10L, 10L, 2000, false);
        attempt(learner, contest, "SUBMITTED", 8L, 10L, 3000, false);
        prizes("[{\"fromRank\":1,\"toRank\":1,\"description\":\"Giải tự soạn\"}]");
        publishResults();
        clearInvocations(auth);
        JsonNode rows = response(winners(), 200);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).path("userId").asLong()).isEqualTo(learner);
        verify(auth, times(1)).contactEmails(Set.of(learner));
    }

    @ParameterizedTest
    @CsvSource({"PUBLISHED,false", "DRAFT,false", "DRAFT,true", "CANCELLED,true"})
    void AT18_stateGuardRunsBeforeRankingProfilesOrContactLookup(String state, boolean published) throws Exception {
        if (published) publishResults();
        jdbc.update("UPDATE community.contests SET status=? WHERE id=?", state, contest);
        clearInvocations(auth, attempts);
        error(winners(), 409, "CONTEST_LOCKED");
        verify(auth, never()).contactEmails(anySet());
        verify(auth, never()).publicProfiles(anySet());
        verify(attempts, never()).ranking(anyLong(), anyInt(), anyInt());
        assertThat(audits()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, mode = EnumSource.Mode.EXCLUDE, names = "CONTENT_EDITOR")
    void AT18_everyOtherRoleIsForbiddenBeforeContactLookup(RoleCode role) throws Exception {
        error(get(ADMIN + "/{id}/winners", contest).header("Authorization", bearer(creator, role.name())), 403, "FORBIDDEN");
        verify(auth, never()).contactEmails(anySet());
        assertThat(audits()).isZero();
    }

    @Test
    void AT18_guestsAreUnauthorizedAndEditorCookieCanReadTheAuditedResult() throws Exception {
        error(get(ADMIN + "/{id}/winners", contest), 401, "UNAUTHENTICATED");
        publishResults();
        Cookie cookie = new Cookie("access_token", bearer(creator, "CONTENT_EDITOR").substring(7));
        response(get(ADMIN + "/{id}/winners", contest).cookie(cookie), 200);
        assertThat(audits()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid", "9223372036854775808", "9223372036854775807"})
    void AT18_invalidOrMissingContestUses404(String id) throws Exception {
        error(get(ADMIN + "/" + id + "/winners").header("Authorization", bearer(creator, "CONTENT_EDITOR")), 404, "CONTEST_NOT_FOUND");
        verify(auth, never()).contactEmails(anySet());
    }

    @Test
    void AT18_auditFailureDoesNotReturnContactEmails(CapturedOutput output) throws Exception {
        attempt(learner, contest, "SUBMITTED", 1L, 1L, 2000, false);
        prizes("[{\"fromRank\":1,\"toRank\":1,\"description\":\"Giải\"}]");
        publishResults();
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit)
                .recordDetail(anyLong(), eq("CONTEST_WINNERS_VIEW"), eq("CONTEST"), eq(contest), any());
        JsonNode body = error(winners(), 500, "INTERNAL_ERROR");
        assertThat(body.findValues("email")).isEmpty();
        assertThat(body.toString()).doesNotContain(email(learner));
        assertThat(output.getAll()).doesNotContain(email(learner));
        assertThat(audits()).isZero();
    }

    @Test
    void AT18_otherContestResponsesNeverContainAnEmailFieldOrRawContactValue() throws Exception {
        attempt(learner, contest, "SUBMITTED", 1L, 1L, 2000, false);
        publishResults();
        for (var request : List.of(get("/api/public/community/contests"), get("/api/public/community/contests/{id}", contest),
                get(BASE + "/{id}/results", contest).header("Authorization", bearer(learner)),
                editor(get(ADMIN)), editor(get(ADMIN + "/{id}", contest)), editor(get(ADMIN + "/{id}/participants", contest)))) {
            JsonNode body = response(request, 200);
            assertThat(body.findValues("email")).isEmpty();
            assertThat(body.toString()).doesNotContain(email(learner), email(creator));
        }
    }

    @Test
    void AT19_onlyContestProductionClassesCanCallTheContactEmailPort() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.cnhsk");
        assertThat(classes).isNotEmpty();
        contactEmailRule().check(classes);
    }

    @Test
    void AT19_ruleRejectsOtherPackagesAndConcreteServiceCallsButAllowsContest(@TempDir Path directory) throws Exception {
        String[] names = {"AllowedReader", "LearningReader", "PrefixReader", "ConcreteReader"};
        String[] packages = {"com.cnhsk.community.contest", "com.cnhsk.learning.future", "com.cnhsk.community.contests", "com.cnhsk.auth.future"};
        var arguments = new ArrayList<>(List.of("-classpath", System.getProperty("java.class.path"), "-d", directory.toString()));
        for (int index = 0; index < names.length; index++) {
            Path source = directory.resolve(names[index] + ".java");
            String type = index == 3 ? "com.cnhsk.auth.service.AuthApiService" : "com.cnhsk.auth.api.AuthApi";
            Files.writeString(source, "package " + packages[index] + "; public class " + names[index]
                    + " { public void read(" + type + " api) { api.contactEmails(java.util.Set.of(1L)); } }", StandardCharsets.UTF_8);
            arguments.add(source.toString());
        }
        var errors = new ByteArrayOutputStream();
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, errors, arguments.toArray(String[]::new)))
                .withFailMessage(errors.toString(StandardCharsets.UTF_8)).isZero();
        var failures = contactEmailRule().evaluate(new ClassFileImporter().importPath(directory)).getFailureReport().getDetails();
        assertThat(failures).hasSize(3).noneMatch(detail -> detail.contains("AllowedReader"));
        for (String name : List.of("LearningReader", "PrefixReader", "ConcreteReader")) {
            assertThat(failures).anyMatch(detail -> detail.contains(name));
        }
    }

    private static ArchRule contactEmailRule() {
        return noClasses().that().resideOutsideOfPackage("com.cnhsk.community.contest..")
                .should().callMethodWhere(new DescribedPredicate<JavaMethodCall>("call the raw contact email port") {
                    @Override public boolean test(JavaMethodCall call) {
                        return call.getTarget().getName().equals("contactEmails")
                                && call.getTarget().getOwner().isAssignableTo(AuthApi.class);
                    }
                }).because("raw email is only exposed by the audited contest winner workflow").allowEmptyShould(false);
    }

    private void prizes(String json) { jdbc.update("UPDATE community.contests SET prizes=?::jsonb WHERE id=?", json, contest); }
    private String email(long id) { return jdbc.queryForObject("SELECT email FROM auth.users WHERE id=?", String.class, id); }
    private long audits() { return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE action='CONTEST_WINNERS_VIEW' "
            + "AND target_type='CONTEST' AND target_id=? AND actor_id=?", Long.class, contest, creator); }
    private MockHttpServletRequestBuilder editor(MockHttpServletRequestBuilder request) { return request.header("Authorization", bearer(creator, "CONTENT_EDITOR")); }
    private MockHttpServletRequestBuilder winners() { return editor(get(ADMIN + "/{id}/winners", contest)); }
}
