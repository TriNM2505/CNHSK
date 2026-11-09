package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ExtendWith(OutputCaptureExtension.class)
public class RedeemIntegrationTest extends RedeemFixture {
    @Test void creditCardAppendsTopupAndReturnsIntegralAmountsExplicitNullExpiry() throws Exception {
        String raw = card("CREDIT", 100, admin);
        JsonNode result = response(redeem(learner, raw), 200);
        assertThat(names(result)).containsExactlyInAnyOrder("cardType", "amount", "balance", "plan");
        assertThat(result.path("amount").isIntegralNumber()).isTrue();
        assertThat(result.path("amount").intValue()).isEqualTo(100);
        assertThat(result.path("balance").isIntegralNumber()).isTrue();
        assertThat(result.path("balance").intValue()).isEqualTo(100);
        assertThat(names(result.path("plan"))).containsExactlyInAnyOrder("code", "name", "expiresAt", "active");
        assertThat(result.path("plan").path("code").asText()).isEqualTo("FREE");
        assertThat(result.path("plan").has("expiresAt")).isTrue();
        assertThat(result.path("plan").path("expiresAt").isNull()).isTrue();
        assertThat(result.path("plan").path("active").asBoolean()).isTrue();
        Map<String,Object> row = jdbc.queryForMap("SELECT tx_type,amount,balance_before,balance_after,seq,card_id FROM learning.credit_transactions WHERE user_id=?", learner);
        assertThat(row).containsEntry("tx_type", "TOPUP").containsEntry("amount", 100)
                .containsEntry("balance_before", 0).containsEntry("balance_after", 100).containsEntry("seq", 1);
        assertThat(row.get("card_id")).isEqualTo(cardId(raw));
    }

    @Test void normalizationAcceptsWhitespaceLowercaseAndCrockfordAliases() throws Exception {
        String raw = "011123456789ABCD";
        insert(raw, "CREDIT", 5, admin);
        response(redeem(learner, " oIlI-2345-6789-abcd "), 200);
        assertThat(balance(learner)).isEqualTo(5);
    }

    @Test void allInvalidCausesShareOneErrorAndIncrementBothCounters() throws Exception {
        List<String> invalid = new ArrayList<>();
        invalid.add("bad-format");
        invalid.add(codes.generate());
        String used = card("CREDIT", 10, admin);
        jdbc.update("UPDATE learning.credit_cards SET status='USED',used_by=?,used_at=now() WHERE id=?", learner, cardId(used));
        invalid.add(used);
        String voided = card("CREDIT", 10, admin);
        jdbc.update("UPDATE learning.credit_cards SET status='VOIDED' WHERE id=?", cardId(voided));
        invalid.add(voided);
        String expired = card("CREDIT", 10, admin);
        jdbc.update("UPDATE learning.credit_cards SET expires_at=? WHERE id=?", Timestamp.from(Instant.now().minusSeconds(1)), cardId(expired));
        invalid.add(expired);
        String self = card("CREDIT", 10, learner);
        invalid.add(self);
        String expected = null;
        for (String raw : invalid) {
            long caller = user("LEARNER");
            if (raw.equals(self)) caller = learner;
            JsonNode error = response(redeem(caller, raw), 400);
            assertThat(error.path("error_code").asText()).isEqualTo("INVALID_CARD_CODE");
            if (expected == null) expected = error.path("message").asText();
            assertThat(error.path("message").asText()).isEqualTo(expected);
            assertThat(redis.opsForValue().get(userKey(caller))).isEqualTo("1");
        }
        assertThat(redis.opsForValue().get(ipKey(ip))).isEqualTo(Integer.toString(invalid.size()));
        assertThat(balance(learner)).isZero();
    }

    @Test void strictBodyValidationPrecedesEvenBlockedCountersAndNeverTouchesDatabase() throws Exception {
        redis.opsForValue().set(userKey(learner), "5", java.time.Duration.ofHours(1));
        redis.opsForValue().set(ipKey(ip), "30", java.time.Duration.ofHours(1));
        clearInvocations(auth, ledger, named);
        for (String body : List.of("{}", "{\"code\":null}", "{\"code\":3}", "{\"code\":true}",
                "{\"code\":[]}", "{\"code\":{}}", "[]", "null", "{", "",
                "{\"code\":\"" + "x".repeat(41) + "\"}", "{\"code\":\"x\",\"extra\":1}",
                "{\"code\":\"x\"} {}", "{\"code\":\"x\"} garbage", "{\"code\":\"x\",\"code\":\"y\"}")) {
            JsonNode error = response(request(learner).content(body), 422);
            assertThat(error.path("error_code").asText()).isEqualTo("VALIDATION_FAILED");
        }
        assertThat(redis.opsForValue().get(userKey(learner))).isEqualTo("5");
        assertThat(redis.opsForValue().get(ipKey(ip))).isEqualTo("30");
        verifyNoInteractions(auth, ledger, named);
    }

    @Test void blockedValidCodeCannotBeConsumedAndSpoofedForwardedHeaderIsIgnored() throws Exception {
        for (int count = 0; count < 5; count++) response(redeem(learner, "invalid"), 400);
        String raw = card("CREDIT", 100, admin);
        JsonNode error = response(redeem(learner, raw).header("X-Forwarded-For", "198.51.100.9"), 429);
        assertThat(error.path("retryAfterSeconds").isIntegralNumber()).isTrue();
        assertThat(error.path("retryAfterSeconds").asLong()).isPositive();
        assertThat(redis.opsForValue().get(userKey(learner))).isEqualTo("5");
        assertThat(redis.opsForValue().get(ipKey(ip))).isEqualTo("5");
        assertThat(redis.hasKey(ipKey("198.51.100.9"))).isFalse();
        assertThat(cardStatus(raw)).isEqualTo("UNUSED");
    }

    @Test void ipThirtyFailuresAcrossUsersBlocksNextRequest() throws Exception {
        for (int count = 0; count < 30; count++) response(redeem(user("LEARNER"), "invalid"), 400);
        String raw = card("CREDIT", 100, admin);
        response(redeem(learner, raw), 429);
        assertThat(redis.opsForValue().get(ipKey(ip))).isEqualTo("30");
        assertThat(redis.hasKey(userKey(learner))).isFalse();
        assertThat(cardStatus(raw)).isEqualTo("UNUSED");
    }

    @Test void subscriptionsUseNowForFreeExpiredAndOldExpiryForActiveWithoutResettingQuota() throws Exception {
        for (String state : List.of("FREE", "EXPIRED", "ACTIVE")) {
            long caller = user("LEARNER");
            Instant old = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS).plusSeconds(state.equals("ACTIVE") ? 864000 : -1);
            if (!state.equals("FREE")) jdbc.update("UPDATE auth.users SET plan_code='PREMIUM',plan_expires_at=? WHERE id=?", Timestamp.from(old), caller);
            jdbc.update("UPDATE auth.users SET free_usage='{\"TRANSLATE\":7}'::jsonb,usage_reset_at=? WHERE id=?", Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), caller);
            Instant before = clock.instant();
            String raw = card("SUBSCRIPTION", 30, admin);
            JsonNode result = response(redeem(caller, raw), 200);
            Instant expires = Instant.parse(result.path("plan").path("expiresAt").asText());
            if (state.equals("ACTIVE")) assertThat(expires).isEqualTo(old.truncatedTo(java.time.temporal.ChronoUnit.MICROS).plusSeconds(30L*86400));
            else assertThat(expires).isBetween(before.plusSeconds(30L*86400), clock.instant().plusSeconds(30L*86400));
            assertThat(result.path("amount").asInt()).isZero();
            assertThat(result.path("plan").path("code").asText()).isEqualTo("PREMIUM");
            assertThat(jdbc.queryForObject("SELECT reason FROM learning.credit_transactions WHERE user_id=?", String.class, caller))
                    .isEqualTo("PREMIUM +30 ngày, giá niêm yết 120000.00");
            assertThat(jdbc.queryForObject("SELECT free_usage->>'TRANSLATE' FROM auth.users WHERE id=?", String.class, caller)).isEqualTo("7");
            assertThat(jdbc.queryForObject("SELECT usage_reset_at FROM auth.users WHERE id=?", Timestamp.class, caller).toInstant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        }
    }

    @Test void inactivePremiumStillHonorsAlreadyIssuedCard() throws Exception {
        jdbc.update("UPDATE learning.plans SET is_active=false WHERE code='PREMIUM'");
        String raw = card("SUBSCRIPTION", 30, admin);
        JsonNode result = response(redeem(learner, raw), 200);
        assertThat(result.path("plan").path("active").asBoolean()).isTrue();
        assertThat(cardStatus(raw)).isEqualTo("USED");
    }

    @Test void ledgerAndUpdatePlanFailuresRollBackCardUserAndLedger(CapturedOutput output) throws Exception {
        LedgerService ledgerSpy = AopTestUtils.getUltimateTargetObject(ledger);
        AuthApi authSpy = AopTestUtils.getUltimateTargetObject(auth);
        for (boolean failLedger : List.of(true, false)) {
            String raw = card("SUBSCRIPTION", 30, admin);
            Map<String,Object> planBefore = jdbc.queryForMap("SELECT plan_code,plan_expires_at,free_usage,usage_reset_at FROM auth.users WHERE id=?", learner);
            int ledgerCountBefore = jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=?", Integer.class, learner);
            String userFailuresBefore = redis.opsForValue().get(userKey(learner));
            String ipFailuresBefore = redis.opsForValue().get(ipKey(ip));
            RuntimeException failure = new IllegalStateException(raw + " " + codes.hash(raw), new RuntimeException("synthetic-private-cause"));
            try {
                if (failLedger) doThrow(failure).when(ledgerSpy).append(any());
                else doThrow(failure).when(authSpy).updatePlan(eq(learner), eq("PREMIUM"), any());
                JsonNode error = response(redeem(learner, raw), 500);
                assertThat(names(error)).containsExactlyInAnyOrder("error_code", "message", "request_id");
                assertThat(error.path("error_code").asText()).isEqualTo("INTERNAL_ERROR");
                assertThat(error.path("message").asText()).isEqualTo("Đã xảy ra lỗi nội bộ.");
                assertThat(error.path("request_id").isTextual()).isTrue();
                assertThat(error.path("request_id").asText()).isNotBlank();
                assertThat(error.toString()).doesNotContain(raw, codes.hash(raw), "synthetic-private-cause", "IllegalStateException", "RuntimeException");
                assertThat(cardStatus(raw)).isEqualTo("UNUSED");
                assertThat(jdbc.queryForMap("SELECT used_by,used_at FROM learning.credit_cards WHERE id=?", cardId(raw)))
                        .containsEntry("used_by", null).containsEntry("used_at", null);
                assertThat(balance(learner)).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=?", Integer.class, learner)).isEqualTo(ledgerCountBefore);
                assertThat(jdbc.queryForObject("SELECT plan_code FROM auth.users WHERE id=?", String.class, learner)).isEqualTo("FREE");
                assertThat(jdbc.queryForObject("SELECT plan_expires_at FROM auth.users WHERE id=?", Timestamp.class, learner)).isNull();
                assertThat(jdbc.queryForMap("SELECT plan_code,plan_expires_at,free_usage,usage_reset_at FROM auth.users WHERE id=?", learner)).isEqualTo(planBefore);
                assertThat(redis.opsForValue().get(userKey(learner))).isEqualTo(userFailuresBefore);
                assertThat(redis.opsForValue().get(ipKey(ip))).isEqualTo(ipFailuresBefore);
                assertThat(output.getAll()).doesNotContain(raw, codes.hash(raw), "synthetic-private-cause");
                verify(authSpy).updatePlan(eq(learner), eq("PREMIUM"), any());
                verify(ledgerSpy, times(failLedger ? 1 : 0)).append(any());
            } finally {
                reset(authSpy, ledgerSpy);
            }
        }
    }

    @Test void missingPremiumRollsClaimBackWithoutChangingOtherPlanOwnership() throws Exception {
        String raw = card("SUBSCRIPTION", 30, admin);
        doReturn(List.of()).when(named).query(eq("SELECT name,price FROM learning.plans WHERE code=:code"), anyMap(), any(RowMapper.class));
        response(redeem(learner, raw), 500);
        assertThat(cardStatus(raw)).isEqualTo("UNUSED");
        assertThat(balance(learner)).isZero();
        assertThat(jdbc.queryForObject("SELECT plan_code FROM auth.users WHERE id=?", String.class, learner)).isEqualTo("FREE");
    }

    @Test void successfulInvalidAndMalformedSecretsNeverAppearInLogsResponsesAuditOrExceptions(CapturedOutput output) throws Exception {
        String raw = card("CREDIT", 11, admin);
        String formatted = codes.format(raw);
        String normalized = raw.toLowerCase(Locale.ROOT);
        String hash = codes.hash(raw);
        Set<String> idemBefore = redis.keys("billing:idem:*");
        String ok = mvc.perform(redeem(learner, formatted)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bad = mvc.perform(redeem(user("LEARNER"), normalized)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String invalid = raw + "!";
        String malformed = mvc.perform(request(learner).content("{\"code\":\"" + invalid + "\""))
                .andExpect(status().isUnprocessableEntity()).andReturn().getResponse().getContentAsString();
        String audit = jdbc.queryForList("SELECT detail::text FROM shared.audit_logs WHERE actor_id=?", String.class, learner).toString();
        assertThat(ok + bad + malformed + audit + output.getAll()).doesNotContain(raw, formatted, normalized, hash, invalid);
        assertThat(new com.cnhsk.learning.credit.web.RedeemRequest(formatted).toString()).doesNotContain(raw, formatted);
        assertThat(redis.keys("billing:idem:*")).isEqualTo(idemBefore);
        assertThat(ok).doesNotContain("cardId", "card_id", "batchId", "code_hash");
    }

    @Test void warmedHundredUniqueSuccessfulCreditPostsReportRealEndToEndTimings() throws Exception {
        String[] raw = new String[120];
        for (int i=0; i<raw.length; i++) raw[i]=card("CREDIT", 1, admin);
        String token = bearer(learner, "LEARNER");
        long[] timings = new long[100];
        for (int i=0; i<120; i++) {
            MockHttpServletRequestBuilder call = post(BASE).header("Authorization", token)
                    .with(request -> { request.setRemoteAddr(ip); return request; })
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("code", raw[i])));
            long start=System.nanoTime();
            var result=mvc.perform(call).andReturn();
            long elapsed=System.nanoTime()-start;
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            JsonNode body=mapper.readTree(result.getResponse().getContentAsByteArray());
            assertThat(body.path("amount").asInt()).isEqualTo(1);
            assertThat(body.path("balance").asInt()).isEqualTo(i+1);
            if(i>=20) timings[i-20]=elapsed;
        }
        assertThat(balance(learner)).isEqualTo(120);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=?", Integer.class, learner)).isEqualTo(120);
        long[] sorted=timings.clone(); Arrays.sort(sorted);
        System.out.println("REDEEM_NFR_NS samples="+Arrays.toString(timings)+" p50="+sorted[49]+" p95="+sorted[94]+" max="+sorted[99]);
    }

    private static Set<String> names(JsonNode node) { Set<String> names=new HashSet<>(); node.fieldNames().forEachRemaining(names::add); return names; }

}

@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
abstract class RedeemFixture extends AbstractIntegrationTest {
        protected static final String BASE="/api/credits/redeem";
        private static final String PASSWORD=new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
        @Autowired protected MockMvc mvc;
        @Autowired protected ObjectMapper mapper;
        @Autowired protected JdbcTemplate jdbc;
        @Autowired protected JwtService jwt;
        @Autowired protected CardCodes codes;
        @Autowired protected Clock clock;
        @Autowired protected StringRedisTemplate redis;
        @MockitoSpyBean protected LedgerService ledger;
        @MockitoSpyBean protected AuthApi auth;
        @MockitoSpyBean protected NamedParameterJdbcTemplate named;
        protected long admin;
        protected long learner;
        protected String ip;
        private final List<Long> users=new ArrayList<>();
        private final Map<String,Long> cards=new HashMap<>();
        private final Set<String> ips=new HashSet<>();
        private Map<String,Object> premiumBefore;
        private int insertedPremium;

        @BeforeEach void setupFixtures() {
            ip="redeem-test-"+UUID.randomUUID(); ips.add(ip);
            admin=user("ADMIN"); learner=user("LEARNER");
            List<Map<String,Object>> rows=jdbc.queryForList("SELECT name,price,is_active FROM learning.plans WHERE code='PREMIUM'");
            premiumBefore=rows.isEmpty()?null:rows.get(0);
            insertedPremium=jdbc.update("INSERT INTO learning.plans(code,name,duration_days,price,is_active) VALUES ('PREMIUM','Synthetic redeem premium',30,120000.00,true) ON CONFLICT(code) DO NOTHING");
            if(premiumBefore==null) assertThat(insertedPremium).isEqualTo(1);
            else assertThat(insertedPremium).isZero();
            jdbc.update("UPDATE learning.plans SET price=120000.00,is_active=true WHERE code='PREMIUM'");
        }

        @AfterEach void cleanOwnedFixtures() {
            reset(AopTestUtils.getUltimateTargetObject(auth), AopTestUtils.getUltimateTargetObject(ledger), AopTestUtils.getUltimateTargetObject(named));
            for(long user:users) jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id=?",user);
            for(long card:cards.values()) jdbc.update("DELETE FROM learning.credit_cards WHERE id=?",card);
            for(long user:users) {
                jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=?",user);
                jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?",user);
                jdbc.update("DELETE FROM auth.users WHERE id=?",user);
                redis.delete(userKey(user));
            }
            for(String address:ips) redis.delete(ipKey(address));
            if(insertedPremium==1) jdbc.update("DELETE FROM learning.plans WHERE code='PREMIUM'");
            else if(premiumBefore!=null) jdbc.update("UPDATE learning.plans SET name=?,price=?,is_active=? WHERE code='PREMIUM'",
                    premiumBefore.get("name"),premiumBefore.get("price"),premiumBefore.get("is_active"));
            users.clear(); cards.clear(); ips.clear();
        }

        protected long user(String role) {
            long id=jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Synthetic redeem') RETURNING id",
                    Long.class,"redeem-"+UUID.randomUUID()+"@example.test",PASSWORD);
            jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,?)",id,role);
            users.add(id); return id;
        }
        protected String card(String type,int value,long creator) {
            String raw=codes.generate(); insert(raw,type,value,creator); return raw;
        }
        protected void insert(String raw,String type,int value,long creator) {
            long id=jdbc.queryForObject("INSERT INTO learning.credit_cards(batch_id,code_hash,card_type,value,plan_days,created_by) VALUES (?,?,?,?,?,?) RETURNING id",
                    Long.class,codes.newBatchId(),codes.hash(raw),type,value,type.equals("SUBSCRIPTION")?value:null,creator);
            cards.put(raw,id);
        }
        protected long cardId(String raw) { return cards.get(raw); }
        protected String cardStatus(String raw) { return jdbc.queryForObject("SELECT status FROM learning.credit_cards WHERE id=?",String.class,cardId(raw)); }
        protected int balance(long user) { return ledger.balance(user); }
        protected String bearer(long user,String role) { return "Bearer "+jwt.issueAccessToken(user,List.of(role),false,UUID.randomUUID()); }
        protected MockHttpServletRequestBuilder request(long user) {
            return post(BASE).header("Authorization",bearer(user,"LEARNER")).contentType(MediaType.APPLICATION_JSON)
                    .with(request->{request.setRemoteAddr(ip);return request;});
        }
        protected MockHttpServletRequestBuilder redeem(long user,String raw) throws Exception { return request(user).content(mapper.writeValueAsBytes(Map.of("code",raw))); }
        protected JsonNode response(MockHttpServletRequestBuilder call,int status) throws Exception {
            return mapper.readTree(mvc.perform(call).andExpect(status().is(status)).andReturn().getResponse().getContentAsByteArray());
        }
        protected String userKey(long user) { return "billing:redeem:fail:user:"+user; }
        protected String ipKey(String address) { ips.add(address); return "billing:redeem:fail:ip:"+address; }
}

