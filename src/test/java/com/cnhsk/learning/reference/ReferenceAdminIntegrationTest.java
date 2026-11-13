package com.cnhsk.learning.reference;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.reference.ReferenceAdminService.Action;
import com.cnhsk.learning.reference.ReferenceAdminService.ReferenceInput;
import com.cnhsk.learning.reference.ReferenceItem.Kind;
import com.cnhsk.learning.reference.ReferenceItem.Status;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferenceAdminIntegrationTest extends AbstractIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-11-14T00:00:00Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate named;
    @Autowired private ReferenceUrlPolicy policy;
    @Autowired private AuditService audit;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ObjectMapper mapper;
    private AuthApi auth;
    private long editor;

    @BeforeEach
    void setup() {
        editor = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Tu soan");
        auth = mock(AuthApi.class);
        when(auth.currentUser()).thenReturn(new CurrentUser(editor, Set.of(RoleCode.CONTENT_EDITOR), false));
    }

    @AfterEach
    void cleanupOnlyOwnedFixtures() {
        jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=?", editor);
        jdbc.update("DELETE FROM learning.reference_items WHERE created_by=?", editor);
        jdbc.update("DELETE FROM auth.users WHERE id=?", editor);
    }

    @ParameterizedTest
    @CsvSource({"DRAFT,PUBLISH,PUBLISHED,true", "PUBLISHED,PUBLISH,PUBLISHED,false", "HIDDEN,PUBLISH,PUBLISHED,true",
            "DRAFT,HIDE,HIDDEN,true", "PUBLISHED,HIDE,HIDDEN,true", "HIDDEN,HIDE,HIDDEN,false",
            "DRAFT,UNHIDE,DRAFT,false", "PUBLISHED,UNHIDE,PUBLISHED,false", "HIDDEN,UNHIDE,DRAFT,true"})
    void FR014_AT11_everyTransitionAndNoOpPreservesTheRequiredAuditAndReportSemantics(
            Status source, Action action, Status destination, boolean changed) throws Exception {
        long id = seed(source, 0);
        report(id, BASE.plusMillis(500), "REFERENCE_REPORTED", "REFERENCE");
        var response = write(service(BASE.plusSeconds(2), named, audit), s -> s.transition(Long.toString(id), action));
        assertThat(response.status()).isEqualTo(destination);
        assertThat(response.updatedAt()).isEqualTo(changed ? BASE.plusSeconds(2) : BASE);
        assertThat(response.reportCount()).isEqualTo(changed ? 0 : 1);
        assertThat(mutations(id)).isEqualTo(changed ? 1 : 0);
        Instant firstPublication = source == Status.DRAFT ? null : BASE.minusSeconds(60);
        assertThat(response.publishedAt()).isEqualTo(action == Action.PUBLISH && source == Status.DRAFT
                ? BASE.plusSeconds(2) : firstPublication);
        if (changed) {
            var log = jdbc.queryForMap("SELECT action,detail::text AS detail FROM shared.audit_logs "
                    + "WHERE actor_id=? AND target_id=? AND action<>'REFERENCE_REPORTED'", editor, id);
            assertThat(log.get("action")).isEqualTo(switch(action) {
                case PUBLISH -> "REFERENCE_PUBLISHED"; case HIDE -> "REFERENCE_HIDDEN"; case UNHIDE -> "REFERENCE_UNHIDDEN";
            });
            var detail = mapper.readTree(log.get("detail").toString());
            assertThat(detail.path("before").path("status").asText()).isEqualTo(source.name());
            assertThat(detail.path("after").path("status").asText()).isEqualTo(destination.name());
        }
    }

    @Test
    void FR012_AT16_createAndFullReplacementKeepStatusAndCreatorWhileResettingOmittedFields() throws Exception {
        var created = write(service(BASE, named, audit), s -> s.create(new ReferenceInput(Kind.BOOK, "Demo", "Mo ta",
                "https://fahasa.com/demo", "https://i.ytimg.com/demo.jpg", 1, 3, List.of("demo"), 7,
                "Tac gia", "NXB", 2026, true)));
        assertThat(created.status()).isEqualTo(Status.DRAFT);
        var replacement = new ReferenceInput(Kind.PODCAST, "Podcast", null, "https://open.spotify.com/show/demo",
                null, null, null, List.of(), 0, null, null, null, false);
        var updated = write(service(BASE.plusSeconds(2), named, audit), s -> s.update(Long.toString(created.id()), replacement));
        assertThat(updated.status()).isEqualTo(Status.DRAFT);
        assertThat(updated.description()).isNull();
        assertThat(updated.tags()).isEmpty();
        assertThat(updated.sortOrder()).isZero();
        assertThat(jdbc.queryForObject("SELECT created_by FROM learning.reference_items WHERE id=?", Long.class, created.id()))
                .isEqualTo(editor);
        assertThat(jdbc.queryForMap("SELECT author,publisher,published_year,is_affiliate FROM learning.reference_items WHERE id=?",
                created.id())).containsEntry("author", null).containsEntry("publisher", null)
                .containsEntry("published_year", null).containsEntry("is_affiliate", false);
        assertThat(mutations(created.id())).isEqualTo(2);
        var initial = mapper.readTree(jdbc.queryForObject("SELECT detail::text FROM shared.audit_logs "
                + "WHERE actor_id=? AND action='REFERENCE_CREATED'", String.class, editor));
        assertThat(initial.path("before").isNull()).isTrue();
        assertThat(initial.path("after").path("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void FR013_AT10_failedPublishOrPublishedEditLeavesEveryFieldAndAuditUnchanged() {
        long id = seed(Status.DRAFT, 0);
        jdbc.update("UPDATE learning.reference_items SET author=null WHERE id=?", id);
        error(() -> write(service(BASE.plusSeconds(1), named, audit), s -> s.transition(Long.toString(id), Action.PUBLISH)),
                ErrorCode.PUBLISH_INVALID_RESOURCE);
        assertThat(mutations(id)).isZero();
        jdbc.update("UPDATE learning.reference_items SET status='PUBLISHED',author='Old author',published_at=? WHERE id=?",
                Timestamp.from(BASE.minusSeconds(60)), id);
        Map<String,Object> before = snapshot(id);
        var invalid = new ReferenceInput(Kind.BOOK, "Changed", null, "https://fahasa.com/demo", null,
                null, null, List.of(), 0, null, null, null, false);
        error(() -> write(service(BASE.plusSeconds(2), named, audit), s -> s.update(Long.toString(id), invalid)),
                ErrorCode.PUBLISH_INVALID_RESOURCE);
        for (ReferenceInput missing : List.of(
                new ReferenceInput(Kind.BOOK, " ", null, "https://fahasa.com/demo", null,
                        null, null, List.of(), 0, "Author", null, null, false),
                new ReferenceInput(Kind.BOOK, "Changed", null, "", null,
                        null, null, List.of(), 0, "Author", null, null, false))) {
            error(() -> write(service(BASE.plusSeconds(3), named, audit), s -> s.update(Long.toString(id), missing)),
                    ErrorCode.PUBLISH_INVALID_RESOURCE);
        }
        assertThat(snapshot(id)).isEqualTo(before);
        assertThat(mutations(id)).isZero();
    }

    @Test
    void FR013_publishRechecksUrlAndAlreadyPublishedIsANoOpEvenForLegacyInvalidData() {
        long id = seed(Status.DRAFT, 0);
        jdbc.update("UPDATE learning.reference_items SET url='https://evil.example/demo' WHERE id=?", id);
        error(() -> write(service(BASE.plusSeconds(2), named, audit), s -> s.transition(Long.toString(id), Action.PUBLISH)),
                ErrorCode.DOMAIN_NOT_ALLOWED);
        assertThat(mutations(id)).isZero();
        jdbc.update("UPDATE learning.reference_items SET status='PUBLISHED',published_at=?,author=null WHERE id=?",
                Timestamp.from(BASE.minusSeconds(60)), id);
        var unchanged = write(service(BASE.plusSeconds(3), named, audit), s -> s.transition(Long.toString(id), Action.PUBLISH));
        assertThat(unchanged.status()).isEqualTo(Status.PUBLISHED);
        assertThat(unchanged.updatedAt()).isEqualTo(BASE);
        assertThat(mutations(id)).isZero();
    }

    @Test
    void FR011_AT14_reportedFilterRunsBeforePaginationAndUsesTheSameInclusiveCutoffForTotal() {
        for(int i=0;i<6;i++) seed(Status.DRAFT, i);
        List<Long> reported = new ArrayList<>();
        for(int i=0;i<3;i++) {
            long id=seed(Status.PUBLISHED, 10+i);
            report(id, BASE, "REFERENCE_REPORTED", "REFERENCE");
            report(id, BASE.plusSeconds(1), "REFERENCE_REPORTED", "REFERENCE");
            reported.add(id);
        }
        long old = seed(Status.HIDDEN, -1);
        report(old, BASE.minusSeconds(1), "REFERENCE_REPORTED", "REFERENCE");
        report(old, BASE.plusSeconds(1), "REFERENCE_UPDATED", "REFERENCE");
        report(old, BASE.plusSeconds(1), "REFERENCE_REPORTED", "OTHER");
        var first=service(BASE, named, audit).list(null, null, true, 0, 2);
        assertThat(first.items().stream().map(ReferenceAdminService.AdminReference::id)).containsExactlyElementsOf(reported.subList(0,2));
        assertThat(first.total()).isEqualTo(3);
        assertThat(first.items()).allSatisfy(item -> {
            assertThat(item.reportCount()).isEqualTo(2);
            assertThat(item.lastReportedAt()).isEqualTo(BASE.plusSeconds(1));
        });
        var second=service(BASE, named, audit).list(null, null, true, 1, 2);
        assertThat(second.items().stream().map(ReferenceAdminService.AdminReference::id)).containsExactly(reported.get(2));
        assertThat(second.total()).isEqualTo(3);
        assertThat(service(BASE,named,audit).list("BOOK","HIDDEN",true,0,20).items()).isEmpty();
        assertThat(service(BASE,named,audit).list(null,null,false,0,20).total()).isEqualTo(10);
    }

    @Test
    void FR011_AT14_countAndPagePlusOneAuditCallStayConstantWhenPageSizeGrows() {
        for(int i=0;i<50;i++) seed(Status.DRAFT,i);
        var storage=spy(named);
        AuditService counted=mock(AuditService.class);
        when(counted.countByTargets(anyString(),anyString(),anySet(),anyMap())).thenAnswer(call ->
                audit.countByTargets(call.getArgument(0),call.getArgument(1),call.getArgument(2),call.getArgument(3)));
        assertThat(service(BASE,storage,counted).list(null,null,null,0,50).items()).hasSize(50);
        verify(storage,times(1)).queryForObject(anyString(),any(SqlParameterSource.class),any(Class.class));
        verify(storage,times(1)).query(anyString(),any(SqlParameterSource.class),any(RowMapper.class));
        verify(counted,times(1)).countByTargets(anyString(),anyString(),anySet(),anyMap());
    }

    @Test
    void FR017_auditFailureRollsBackCreateUpdateAndTransition() {
        long id=seed(Status.DRAFT,0);
        AuditService broken=mock(AuditService.class);
        doThrow(new IllegalStateException("Simulated audit failure")).when(broken)
                .recordDetail(anyLong(),anyString(),anyString(),anyLong(),anyMap());
        var service=service(BASE.plusSeconds(2),named,broken);
        assertThatThrownBy(() -> write(service,s -> s.create(input()))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.reference_items WHERE created_by=?",Long.class,editor)).isEqualTo(1);
        Map<String,Object> before=snapshot(id);
        assertThatThrownBy(() -> write(service,s -> s.update(Long.toString(id),input()))).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot(id)).isEqualTo(before);
        assertThatThrownBy(() -> write(service,s -> s.transition(Long.toString(id),Action.HIDE))).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot(id)).isEqualTo(before);
        assertThat(mutations(id)).isZero();
    }

    @Test
    void FR014_simultaneousPublishRequestsSerializeAndOnlyOneWritesAudit() throws Exception {
        long id=seed(Status.DRAFT,0);
        var pool=Executors.newFixedThreadPool(2);
        var start=new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<ReferenceAdminService.AdminReference>> results=new ArrayList<>();
            for(int i=0;i<2;i++) results.add(pool.submit(() -> {
                start.await();
                return write(service(BASE.plusSeconds(2),named,audit),s -> s.transition(Long.toString(id),Action.PUBLISH));
            }));
            start.countDown();
            for(var result:results) assertThat(result.get(20,TimeUnit.SECONDS).status()).isEqualTo(Status.PUBLISHED);
            assertThat(mutations(id)).isEqualTo(1);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue(); }
    }

    private ReferenceAdminService service(Instant now,NamedParameterJdbcTemplate storage,AuditService writer) {
        return new ReferenceAdminService(auth,storage,policy,writer,Clock.fixed(now,ZoneOffset.UTC));
    }
    private <T>T write(ReferenceAdminService service,Function<ReferenceAdminService,T> operation) {
        return new TransactionTemplate(transactions).execute(status -> operation.apply(service));
    }
    private ReferenceInput input() {
        return new ReferenceInput(Kind.BOOK,"Replacement",null,"https://fahasa.com/demo",null,null,null,
                List.of(),0,"Author",null,null,false);
    }
    private long seed(Status state,int order) {
        return jdbc.queryForObject("INSERT INTO learning.reference_items(kind,title,url,author,status,sort_order,created_by,"
                + "published_at,updated_at) VALUES ('BOOK','Demo','https://fahasa.com/demo','Author',?,?,?,?,?) RETURNING id",
                Long.class,state.name(),order,editor,state==Status.DRAFT?null:Timestamp.from(BASE.minusSeconds(60)),Timestamp.from(BASE));
    }
    private void report(long id,Instant when,String action,String type) {
        jdbc.update("INSERT INTO shared.audit_logs(actor_id,action,target_type,target_id,created_at,detail) VALUES (?,?,?,?,?,'{}')",
                editor,action,type,id,Timestamp.from(when));
    }
    private long mutations(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=? AND target_type='REFERENCE' "
                + "AND target_id=? AND action<>'REFERENCE_REPORTED'",Long.class,editor,id);
    }
    private Map<String,Object> snapshot(long id) {
        return jdbc.queryForMap("SELECT title,author,status,tags::text,published_at,updated_at FROM learning.reference_items WHERE id=?",id);
    }
    private void error(Runnable action,ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
