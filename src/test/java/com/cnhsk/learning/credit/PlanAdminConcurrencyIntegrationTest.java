package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.auth.dto.RegisterRequest;
import com.cnhsk.auth.dto.RegisterResponse;
import com.cnhsk.auth.service.RegistrationService;
import com.cnhsk.learning.credit.web.Benefits;
import com.cnhsk.learning.credit.web.PlanStatus.PlanCode;
import com.cnhsk.learning.credit.web.PlanUpdateRequest;
import com.cnhsk.learning.credit.web.PlanView;
import com.cnhsk.learning.credit.web.RedeemRequest;
import com.cnhsk.learning.credit.web.RedeemResult;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class PlanAdminConcurrencyIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2031-11-15T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Map<String, Integer> INITIAL_BENEFITS = Map.of(
            "AI_GENERATE", 10, "TRANSLATE", 10, "ASSISTANT", 10, "GRADING", 10);
    private static final Map<String, Integer> USAGE = Map.of("TRANSLATE", 7);
    private static final Instant RESET = Instant.parse("2031-10-31T17:00:00Z");

    @Autowired private AuthApi auth;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate named;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private ObjectMapper mapper;
    @Autowired private AuditService audit;
    @Autowired private LedgerService ledger;
    @Autowired private CardCodes codes;
    @Autowired private RedeemRateLimitService attempts;
    @Autowired private RegistrationService registration;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> cardIds = new ArrayList<>();
    private List<Map<String, Object>> catalogBefore;
    private List<Map<String, Object>> auditBefore;
    private long admin;
    private long creator;
    private long learner;
    private String ip;
    private PlanAdminService plans;
    private RedeemService redeem;

    @BeforeEach
    void setUp() {
        catalogBefore = catalogRows();
        auditBefore = auditRows();
        admin = user("ADMIN");
        creator = user("ADMIN");
        learner = user("LEARNER");
        ip = "plan-concurrency-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO learning.plans(code,name,description,duration_days,price,benefits,is_active)
                VALUES ('PREMIUM',?,?,30,120000.00,CAST(? AS jsonb),true)
                ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,description=EXCLUDED.description,
                    duration_days=EXCLUDED.duration_days,price=EXCLUDED.price,
                    benefits=EXCLUDED.benefits,is_active=EXCLUDED.is_active
                """, initial().name(), initial().description(), jsonText(INITIAL_BENEFITS));
        assertThat(auth.countActivePlan("PREMIUM", NOW)).as("isolated active-user baseline at the fixed guard clock").isZero();
        plans = new PlanAdminService(auth, named, mapper, audit, FIXED_CLOCK);
        redeem = new RedeemService(auth, named, ledger, codes, attempts, FIXED_CLOCK);
        configureLearner(false);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        List<Executable> cleanup = new ArrayList<>();
        for (long id : userIds) cleanup.add(() -> jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=?", id));
        for (long id : userIds) cleanup.add(() -> jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id=?", id));
        for (long id : cardIds) cleanup.add(() -> jdbc.update("DELETE FROM learning.credit_cards WHERE id=?", id));
        for (long id : userIds) {
            cleanup.add(() -> jdbc.update("DELETE FROM auth.auth_tokens WHERE user_id=?", id));
            cleanup.add(() -> jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", id));
            cleanup.add(() -> jdbc.update("DELETE FROM auth.users WHERE id=?", id));
        }
        for (var row : catalogBefore) {
            String code = (String) row.get("code");
            if (!code.equals("FREE") && !code.equals("PREMIUM")) continue;
            cleanup.add(() -> jdbc.update("""
                    INSERT INTO learning.plans(code,name,description,duration_days,price,benefits,is_active,created_at)
                    VALUES (?,?,?,?,?,CAST(? AS jsonb),?,?)
                    ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,description=EXCLUDED.description,
                        duration_days=EXCLUDED.duration_days,price=EXCLUDED.price,benefits=EXCLUDED.benefits,
                        is_active=EXCLUDED.is_active,created_at=EXCLUDED.created_at
                    """, code, row.get("name"), row.get("description"), row.get("duration_days"), row.get("price"),
                    row.get("benefits"), row.get("is_active"), row.get("created_at")));
        }
        if (catalogBefore.stream().noneMatch(row -> row.get("code").equals("PREMIUM"))) {
            cleanup.add(() -> jdbc.update("""
                    DELETE FROM learning.plans p WHERE p.code='PREMIUM'
                    AND NOT EXISTS (SELECT 1 FROM auth.users u WHERE u.plan_code=p.code)
                    """));
        }
        cleanup.add(() -> assertThat(catalogRows()).isEqualTo(catalogBefore));
        cleanup.add(() -> assertThat(auditRows()).isEqualTo(auditBefore));
        for (long id : userIds) cleanup.add(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM auth.users WHERE id=?", Long.class, id)).isZero());
        assertAll("Restore owned concurrency fixtures and retain every failure", cleanup.stream());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void committedSubscriptionMakesWaitingBenefitsEditRejectTheActiveUser(boolean expired) throws Exception {
        configureLearner(expired);
        IssuedCard card = card("SUBSCRIPTION", 30);
        Race<RedeemResult, PlanView> race = contend(
                () -> redeemAs(learner, redeem, card),
                () -> updateAs(admin, plans, changedBenefits()), false);
        assertSubscription(success(race.first()), card, learner, initial(), NOW.plusSeconds(30L * 86400));
        assertPlanInUse(race.second());
        assertThat(premiumSnapshot()).isEqualTo(snapshot(initial()));
        assertThat(auditCount(admin, "PLAN_UPDATED")).isZero();
        assertUsage(learner);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitingIssuedSubscriptionUsesCommittedInactiveCatalogAndRenewsFromNow(boolean expired) throws Exception {
        configureLearner(expired);
        IssuedCard card = card("SUBSCRIPTION", 30);
        PlanUpdateRequest changed = changedListing();
        Race<PlanView, RedeemResult> race = contend(
                () -> updateAs(admin, plans, changed),
                () -> redeemAs(learner, redeem, card), false);
        assertThat(success(race.first()).isActive()).isFalse();
        assertSubscription(success(race.second()), card, learner, changed, NOW.plusSeconds(30L * 86400));
        assertAudit(admin, "PLAN_UPDATED", snapshot(initial()), snapshot(changed));
        assertThat(premiumSnapshot()).isEqualTo(snapshot(changed));
        assertUsage(learner);
    }

    @Test
    void freeCatalogEditDoesNotBlockActualRegistrationForeignKey() throws Exception {
        String email = "plan-registration-" + UUID.randomUUID() + "@example.test";
        PlanUpdateRequest request = new PlanUpdateRequest("Free concurrency", "Updated free description",
                null, null, null, null);
        Race<PlanView, RegisterResponse> race = whileHeld(
                () -> asActor(admin, Set.of("ADMIN"), () -> plans.updatePlan(PlanCode.FREE, request)),
                () -> {
                    RegisterResponse result = registration.register(
                            new RegisterRequest(email, "StrongPass123", "StrongPass123", "New learner", true));
                    userIds.add(result.userId());
                    return result;
                });
        assertThat(success(race.first()).name()).isEqualTo(request.name());
        RegisterResponse registered = success(race.second());
        assertThat(registered.email()).isEqualTo(email);
        assertThat(registered.emailVerified()).isFalse();
        assertThat(jdbc.queryForMap("SELECT plan_code,plan_expires_at FROM auth.users WHERE id=?",
                registered.userId())).containsEntry("plan_code", "FREE").containsEntry("plan_expires_at", null);
        assertThat(jdbc.queryForList("SELECT role_code FROM auth.user_roles WHERE user_id=?",
                String.class, registered.userId())).containsExactly("LEARNER");
        assertThat(jdbc.queryForList("SELECT token_type FROM auth.auth_tokens WHERE user_id=?",
                String.class, registered.userId())).containsExactly("EMAIL_VERIFY");
        assertThat(auditCount(admin, "PLAN_UPDATED")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentAbsentPremiumCreationHasOneOwnerAndCommittedBeforeAfter(boolean rollbackFirst) throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE plan_code='PREMIUM'",
                Integer.class)).as("absent-catalog fixture must not reassign unrelated users").isZero();
        assertThat(jdbc.update("""
                DELETE FROM learning.plans p WHERE p.code='PREMIUM'
                AND NOT EXISTS (SELECT 1 FROM auth.users u WHERE u.plan_code=p.code)
                """)).isEqualTo(1);
        PlanUpdateRequest first = new PlanUpdateRequest("First created premium", "First description", 30,
                "120000.00", new Benefits(INITIAL_BENEFITS), true);
        PlanUpdateRequest second = new PlanUpdateRequest("Second premium", "Second description", 60,
                "240000.50", new Benefits(INITIAL_BENEFITS), false);
        Race<PlanView, PlanView> race = contend(() -> updateAs(admin, plans, first),
                () -> updateAs(creator, plans, second), rollbackFirst);
        assertThat(success(race.first()).exists()).isTrue();
        assertThat(success(race.second()).name()).isEqualTo(second.name());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.plans WHERE code='PREMIUM'",
                Integer.class)).isEqualTo(1);
        assertThat(premiumSnapshot()).isEqualTo(snapshot(second));
        if (rollbackFirst) {
            assertThat(auditCount(admin, "PLAN_CREATED")).isZero();
            assertAudit(creator, "PLAN_CREATED", mapper.getNodeFactory().nullNode(), snapshot(second));
            assertThat(auditCount(creator, "PLAN_UPDATED")).isZero();
        } else {
            assertAudit(admin, "PLAN_CREATED", mapper.getNodeFactory().nullNode(), snapshot(first));
            assertAudit(creator, "PLAN_UPDATED", snapshot(first), snapshot(second));
            assertThat(auditCount(creator, "PLAN_CREATED")).isZero();
        }
        assertThat(auditCount(admin, "PLAN_CREATED") + auditCount(creator, "PLAN_CREATED")).isEqualTo(1);
    }

    @Test
    void creditRedemptionCompletesWhilePremiumCatalogEditRemainsUncommitted() throws Exception {
        Instant expiry = NOW.plusSeconds(10L * 86400);
        jdbc.update("UPDATE auth.users SET plan_code='PREMIUM',plan_expires_at=? WHERE id=?",
                Timestamp.from(expiry), learner);
        IssuedCard card = card("CREDIT", 100);
        Race<PlanView, RedeemResult> race = whileHeld(
                () -> updateAs(admin, plans, changedListing()), () -> redeemAs(learner, redeem, card));
        success(race.first());
        RedeemResult result = success(race.second());
        assertThat(result.cardType().name()).isEqualTo("CREDIT");
        assertThat(result.amount()).isEqualTo(100);
        assertThat(result.balance()).isEqualTo(100);
        assertThat(result.plan().code()).isEqualTo(PlanCode.PREMIUM);
        assertThat(result.plan().name()).isEqualTo(initial().name());
        assertThat(result.plan().expiresAt()).isEqualTo(expiry);
        assertThat(result.plan().active()).isTrue();
        assertUsed(card, learner);
        assertThat(jdbc.queryForMap("""
                SELECT seq,tx_type,amount,balance_before,balance_after FROM learning.credit_transactions WHERE card_id=?
                """, card.id())).containsEntry("seq", 1).containsEntry("tx_type", "TOPUP").containsEntry("amount", 100)
                .containsEntry("balance_before", 0).containsEntry("balance_after", 100);
        assertThat(auth.readPlan(learner).orElseThrow().planExpiresAt()).isEqualTo(expiry);
        assertUsage(learner);
    }

    @Test
    void mixedRoleActorAuditDoesNotDeadlockTheClaimedSubscriptionWaitingForCatalog() throws Exception {
        long mixed = user("ADMIN", "LEARNER");
        IssuedCard card = card("SUBSCRIPTION", 30);
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch releaseClaim = new CountDownLatch(1);
        CountDownLatch catalogLocked = new CountDownLatch(1);
        CountDownLatch releaseAdmin = new CountDownLatch(1);
        GatedJdbc redeemJdbc = new GatedJdbc(jdbc, () -> {
            claimed.countDown();
            await(releaseClaim);
        }, () -> {});
        GatedJdbc adminJdbc = new GatedJdbc(jdbc, () -> {}, () -> {
            catalogLocked.countDown();
            await(releaseAdmin);
        });
        RedeemService gatedRedeem = new RedeemService(auth, redeemJdbc, ledger, codes, attempts, FIXED_CLOCK);
        PlanAdminService gatedAdmin = new PlanAdminService(auth, adminJdbc, mapper, audit, FIXED_CLOCK);
        AtomicInteger redeemPid = new AtomicInteger();
        AtomicInteger adminPid = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt<RedeemResult>> redemption = pool.submit(() -> transaction(
                    () -> asActor(mixed, Set.of("ADMIN", "LEARNER"),
                            () -> gatedRedeem.redeem(new RedeemRequest(card.raw()), ip)),
                    redeemPid, null, null, false));
            await(claimed);
            Future<Attempt<PlanView>> update = pool.submit(() -> transaction(
                    () -> asActor(mixed, Set.of("ADMIN", "LEARNER"),
                            () -> gatedAdmin.updatePlan(PlanCode.PREMIUM, changedListing())),
                    adminPid, null, null, false));
            await(catalogLocked);
            releaseClaim.countDown();
            awaitBlockedBy(redeemPid.get(), adminPid.get(), redemption);
            releaseAdmin.countDown();
            assertThat(success(update.get(8, TimeUnit.SECONDS)).name()).isEqualTo(changedListing().name());
            assertSubscription(success(redemption.get(15, TimeUnit.SECONDS)), card, mixed,
                    changedListing(), NOW.plusSeconds(30L * 86400));
            assertAudit(mixed, "PLAN_UPDATED", snapshot(initial()), snapshot(changedListing()));
        } finally {
            releaseClaim.countDown();
            releaseAdmin.countDown();
            finish(pool);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rolledBackSubscriptionReleasesCatalogForTheWaitingBenefitsEdit(boolean expired) throws Exception {
        configureLearner(expired);
        PlanState before = auth.readPlan(learner).orElseThrow();
        IssuedCard card = card("SUBSCRIPTION", 30);
        Race<RedeemResult, PlanView> race = contend(
                () -> redeemAs(learner, redeem, card),
                () -> updateAs(admin, plans, changedBenefits()), true);
        success(race.first());
        assertThat(success(race.second()).benefits()).containsEntry("TRANSLATE", 11);
        assertThat(auth.readPlan(learner)).contains(before);
        assertThat(jdbc.queryForMap("SELECT status,used_by,used_at FROM learning.credit_cards WHERE id=?", card.id()))
                .containsEntry("status", "UNUSED").containsEntry("used_by", null).containsEntry("used_at", null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE card_id=?",
                Integer.class, card.id())).isZero();
        assertAudit(admin, "PLAN_UPDATED", snapshot(initial()), snapshot(changedBenefits()));
    }

    @Test
    void rolledBackCatalogEditReleasesSubscriptionToTheOriginalListing() throws Exception {
        IssuedCard card = card("SUBSCRIPTION", 30);
        Race<PlanView, RedeemResult> race = contend(
                () -> updateAs(admin, plans, changedListing()),
                () -> redeemAs(learner, redeem, card), true);
        success(race.first());
        assertSubscription(success(race.second()), card, learner, initial(), NOW.plusSeconds(30L * 86400));
        assertThat(premiumSnapshot()).isEqualTo(snapshot(initial()));
        assertThat(auditCount(admin, "PLAN_UPDATED")).isZero();
        assertUsage(learner);
    }

    private <A, B> Race<A, B> contend(Supplier<A> holderAction, Supplier<B> contenderAction,
            boolean rollbackHolder) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderPid = new AtomicInteger();
        AtomicInteger contenderPid = new AtomicInteger();
        try {
            Future<Attempt<A>> holder = pool.submit(() -> transaction(holderAction, holderPid, held, release, rollbackHolder));
            await(held);
            Future<Attempt<B>> contender = pool.submit(() -> transaction(contenderAction, contenderPid, null, null, false));
            awaitPid(contenderPid, contender);
            awaitBlockedBy(contenderPid.get(), holderPid.get(), contender);
            assertThat(contender.isDone()).isFalse();
            release.countDown();
            return new Race<>(holder.get(15, TimeUnit.SECONDS), contender.get(15, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            finish(pool);
        }
    }

    private <A, B> Race<A, B> whileHeld(Supplier<A> holderAction, Supplier<B> independentAction) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderPid = new AtomicInteger();
        AtomicInteger independentPid = new AtomicInteger();
        try {
            Future<Attempt<A>> holder = pool.submit(() -> transaction(holderAction, holderPid, held, release, false));
            await(held);
            Future<Attempt<B>> independent = pool.submit(() -> transaction(
                    independentAction, independentPid, null, null, false));
            Attempt<B> completed = independent.get(8, TimeUnit.SECONDS);
            success(completed);
            assertThat(independentPid.get()).isNotEqualTo(holderPid.get());
            assertThat(release.getCount()).isEqualTo(1);
            assertThat(holder.isDone()).isFalse();
            release.countDown();
            return new Race<>(holder.get(15, TimeUnit.SECONDS), completed);
        } finally {
            release.countDown();
            finish(pool);
        }
    }

    private <T> Attempt<T> transaction(Supplier<T> action, AtomicInteger pid, CountDownLatch held,
            CountDownLatch release, boolean rollback) {
        TransactionTemplate tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            T result = tx.execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '10s'");
                jdbc.execute("SET LOCAL statement_timeout = '15s'");
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                T value = action.get();
                if (held != null) held.countDown();
                if (release != null) await(release);
                if (rollback) status.setRollbackOnly();
                return value;
            });
            return new Attempt<>(result, null);
        } catch (RuntimeException exception) {
            return new Attempt<>(null, exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static <T> T asActor(long id, Set<String> roles, Supplier<T> action) {
        var principal = new AuthenticatedUser(id, roles, false, UUID.randomUUID().toString(), UUID.randomUUID(), NOW);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private PlanView updateAs(long actor, PlanAdminService service, PlanUpdateRequest request) {
        return asActor(actor, Set.of("ADMIN"), () -> service.updatePlan(PlanCode.PREMIUM, request));
    }

    private RedeemResult redeemAs(long actor, RedeemService service, IssuedCard card) {
        return asActor(actor, Set.of("LEARNER"), () -> service.redeem(new RedeemRequest(card.raw()), ip));
    }

    private void configureLearner(boolean expired) {
        jdbc.update("""
                UPDATE auth.users SET plan_code=?,plan_expires_at=?,free_usage=CAST(? AS jsonb),usage_reset_at=? WHERE id=?
                """, expired ? "PREMIUM" : "FREE", expired ? Timestamp.from(NOW.minusSeconds(1)) : null,
                jsonText(USAGE), Timestamp.from(RESET), learner);
    }

    private long user(String... roles) {
        long id = jdbc.queryForObject("""
                INSERT INTO auth.users(email,password_hash,display_name)
                VALUES (?,?,'Plan concurrency') RETURNING id
                """, Long.class, "plan-concurrency-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        userIds.add(id);
        for (String role : roles) jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,?)", id, role);
        return id;
    }

    private IssuedCard card(String type, int value) {
        String raw = codes.generate();
        long id = jdbc.queryForObject("""
                INSERT INTO learning.credit_cards(batch_id,code_hash,card_type,value,plan_days,created_by)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, Long.class, codes.newBatchId(), codes.hash(raw), type, value,
                type.equals("SUBSCRIPTION") ? value : null, creator);
        cardIds.add(id);
        return new IssuedCard(id, raw, value);
    }

    private static PlanUpdateRequest initial() {
        return new PlanUpdateRequest("Initial premium", "Initial listing", 30, "120000.00",
                new Benefits(INITIAL_BENEFITS), true);
    }

    private static PlanUpdateRequest changedListing() {
        return new PlanUpdateRequest("Updated premium", "Updated listing", 45, "350000.50",
                new Benefits(INITIAL_BENEFITS), false);
    }

    private static PlanUpdateRequest changedBenefits() {
        Benefits benefits = new Benefits(INITIAL_BENEFITS);
        benefits.put("TRANSLATE", 11);
        return new PlanUpdateRequest("Updated benefits", "Benefits edit", 30, "120000.00", benefits, true);
    }

    private JsonNode snapshot(PlanUpdateRequest request) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", request.name());
        node.put("description", request.description());
        node.put("durationDays", request.durationDays());
        node.put("price", new BigDecimal(request.price()).setScale(2).toPlainString());
        node.set("benefits", mapper.valueToTree(request.benefits()));
        node.put("isActive", request.isActive());
        return node;
    }

    private JsonNode premiumSnapshot() {
        return json(jdbc.queryForObject("""
                SELECT jsonb_build_object('name',name,'description',description,'durationDays',duration_days,
                    'price',price::text,'benefits',benefits,'isActive',is_active)::text
                FROM learning.plans WHERE code='PREMIUM'
                """, String.class));
    }

    private List<Map<String, Object>> catalogRows() {
        return jdbc.queryForList("""
                SELECT code,name,description,duration_days,price,benefits::text AS benefits,is_active,created_at
                FROM learning.plans ORDER BY code
                """);
    }

    private List<Map<String, Object>> auditRows() {
        return jdbc.queryForList("""
                SELECT id,actor_id,action,target_type,target_id,detail::text AS detail,ip_address::text AS ip_address,created_at
                FROM shared.audit_logs ORDER BY id
                """);
    }

    private void assertSubscription(RedeemResult result, IssuedCard card, long actor,
            PlanUpdateRequest listing, Instant expiry) {
        assertThat(result.cardType().name()).isEqualTo("SUBSCRIPTION");
        assertThat(result.amount()).isZero();
        assertThat(result.balance()).isZero();
        assertThat(result.plan().code()).isEqualTo(PlanCode.PREMIUM);
        assertThat(result.plan().name()).isEqualTo(listing.name());
        assertThat(result.plan().expiresAt()).isEqualTo(expiry);
        assertThat(result.plan().active()).isTrue();
        assertUsed(card, actor);
        assertThat(jdbc.queryForMap("""
                SELECT seq,tx_type,amount,balance_before,balance_after,reason
                FROM learning.credit_transactions WHERE card_id=? AND user_id=?
                """, card.id(), actor)).containsEntry("seq", 1).containsEntry("tx_type", "SUBSCRIPTION")
                .containsEntry("amount", 0).containsEntry("balance_before", 0).containsEntry("balance_after", 0)
                .containsEntry("reason", "PREMIUM +" + card.value() + " ngày, giá niêm yết " + listing.price());
        PlanState state = auth.readPlan(actor).orElseThrow();
        assertThat(state.planCode()).isEqualTo("PREMIUM");
        assertThat(state.planExpiresAt()).isEqualTo(expiry);
    }

    private void assertUsed(IssuedCard card, long actor) {
        assertThat(jdbc.queryForMap("SELECT status,used_by,used_at FROM learning.credit_cards WHERE id=?", card.id()))
                .containsEntry("status", "USED").containsEntry("used_by", actor).containsEntry("used_at", Timestamp.from(NOW));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE card_id=?",
                Integer.class, card.id())).isEqualTo(1);
    }

    private void assertUsage(long actor) {
        PlanState state = auth.readPlan(actor).orElseThrow();
        assertThat(state.freeUsage()).isEqualTo(USAGE);
        assertThat(state.usageResetAt()).isEqualTo(RESET);
    }

    private long auditCount(long actor, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=? AND action=?",
                Long.class, actor, action);
    }

    private void assertAudit(long actor, String action, JsonNode before, JsonNode after) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT target_type,target_id,detail::text AS detail FROM shared.audit_logs
                WHERE actor_id=? AND action=? ORDER BY id
                """, actor, action);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("target_type", "PLAN").containsEntry("target_id", null);
        JsonNode detail = json((String) rows.get(0).get("detail"));
        assertThat(detail.path("code").asText()).isEqualTo("PREMIUM");
        assertThat(detail.path("before")).isEqualTo(before);
        assertThat(detail.path("after")).isEqualTo(after);
    }

    private static void assertPlanInUse(Attempt<?> attempt) {
        assertThat(attempt.failure()).isInstanceOf(ApiException.class);
        ApiException failure = (ApiException) attempt.failure();
        assertThat(failure.getCode()).isEqualTo(ErrorCode.PLAN_IN_USE);
        assertThat(failure.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    private static <T> T success(Attempt<T> attempt) {
        assertThat(attempt.failure()).isNull();
        return attempt.value();
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Test fixture JSON is invalid.", exception);
        }
    }

    private String jsonText(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Test fixture JSON cannot be serialized.", exception);
        }
    }

    private void awaitBlockedBy(int waitingPid, int blockingPid, Future<?> waiting) {
        assertThat(waitingPid).isNotEqualTo(blockingPid);
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT ?=ANY(pg_blocking_pids(?))", Boolean.class, blockingPid, waitingPid))) {
            if (waiting.isDone()) throw new AssertionError("Contender finished before PostgreSQL lock contention was observed.");
            if (System.nanoTime() >= deadline) throw new AssertionError("Expected PostgreSQL blocker was not observed.");
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    private static void awaitPid(AtomicInteger pid, Future<?> worker) {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (pid.get() == 0) {
            if (worker.isDone()) throw new AssertionError("Worker completed before publishing its PostgreSQL backend.");
            if (System.nanoTime() >= deadline) throw new AssertionError("Worker did not publish its PostgreSQL backend.");
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating plan transactions.", exception);
        }
    }

    private static void finish(ExecutorService pool) throws InterruptedException {
        pool.shutdownNow();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
    }

    private record Attempt<T>(T value, RuntimeException failure) {}
    private record Race<A, B>(Attempt<A> first, Attempt<B> second) {}
    private record IssuedCard(long id, String raw, int value) {}

    private static final class GatedJdbc extends NamedParameterJdbcTemplate {
        private final Runnable afterClaim;
        private final Runnable afterCatalogLock;

        GatedJdbc(JdbcTemplate jdbc, Runnable afterClaim, Runnable afterCatalogLock) {
            super(jdbc);
            this.afterClaim = afterClaim;
            this.afterCatalogLock = afterCatalogLock;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource parameters, RowMapper<T> rowMapper) {
            List<T> rows = super.query(sql, parameters, rowMapper);
            if (sql.contains("UPDATE learning.credit_cards") && !rows.isEmpty()) afterClaim.run();
            return rows;
        }

        @Override
        public <T> List<T> query(String sql, Map<String, ?> parameters, RowMapper<T> rowMapper) {
            List<T> rows = super.query(sql, parameters, rowMapper);
            if (sql.contains("FOR NO KEY UPDATE") && !rows.isEmpty()) afterCatalogLock.run();
            return rows;
        }
    }
}
