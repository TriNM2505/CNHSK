package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.databind.JsonNode;

class RedeemConcurrencyIntegrationTest extends RedeemFixture {
    @Test void tenUsersRedeemingOneCardProduceOneSuccessAndNineIdenticalErrors() throws Exception {
        String raw=card("CREDIT",100,admin);
        List<MockHttpServletRequestBuilder> calls=new ArrayList<>();
        for(int i=0;i<10;i++) calls.add(redeem(user("LEARNER"),raw));
        List<Outcome> results=concurrent(calls);
        assertThat(results.stream().filter(result->result.status()==200).count()).isEqualTo(1);
        List<Outcome> errors=results.stream().filter(result->result.status()==400).toList();
        assertThat(errors).hasSize(9);
        Set<String> messages=new HashSet<>();
        for(Outcome error:errors) {
            JsonNode body=mapper.readTree(error.body());
            assertThat(body.path("error_code").asText()).isEqualTo("INVALID_CARD_CODE");
            messages.add(body.path("message").asText());
        }
        assertThat(messages).containsExactly("Mã thẻ không hợp lệ hoặc không thể sử dụng.");
        assertThat(cardStatus(raw)).isEqualTo("USED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE card_id=?",Integer.class,cardId(raw))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM learning.credit_transactions WHERE card_id=?",Integer.class,cardId(raw))).isEqualTo(100);
        long winner=jdbc.queryForObject("SELECT used_by FROM learning.credit_cards WHERE id=?",Long.class,cardId(raw));
        assertThat(balance(winner)).isEqualTo(100);
    }

    @Test void twoCreditCardsForOneUserPreserveConsecutiveSequenceAndSum() throws Exception {
        String first=card("CREDIT",20,admin);
        String second=card("CREDIT",30,admin);
        List<Outcome> results=concurrent(List.of(redeem(learner,first),redeem(learner,second)));
        assertThat(results).extracting(Outcome::status).containsExactly(200,200);
        assertThat(jdbc.queryForList("SELECT seq FROM learning.credit_transactions WHERE user_id=? ORDER BY seq",Integer.class,learner)).containsExactly(1,2);
        assertThat(jdbc.queryForList("SELECT balance_before FROM learning.credit_transactions WHERE user_id=? ORDER BY seq",Integer.class,learner).get(0)).isZero();
        assertThat(balance(learner)).isEqualTo(50);
        assertThat(cardStatus(first)).isEqualTo("USED");
        assertThat(cardStatus(second)).isEqualTo("USED");
    }

    @Test void twoSubscriptionCardsForOneUserAccumulateBothExtensions() throws Exception {
        Instant old=clock.instant().truncatedTo(ChronoUnit.MICROS).plusSeconds(10L*86400);
        jdbc.update("UPDATE auth.users SET plan_code='PREMIUM',plan_expires_at=? WHERE id=?",Timestamp.from(old),learner);
        String first=card("SUBSCRIPTION",30,admin);
        String second=card("SUBSCRIPTION",15,admin);
        List<Outcome> results=concurrent(List.of(redeem(learner,first),redeem(learner,second)));
        assertThat(results).extracting(Outcome::status).containsExactly(200,200);
        assertThat(jdbc.queryForObject("SELECT plan_expires_at FROM auth.users WHERE id=?",Timestamp.class,learner).toInstant()).isEqualTo(old.plusSeconds(45L*86400));
        assertThat(jdbc.queryForList("SELECT seq FROM learning.credit_transactions WHERE user_id=? ORDER BY seq",Integer.class,learner)).containsExactly(1,2);
        assertThat(jdbc.queryForList("SELECT amount FROM learning.credit_transactions WHERE user_id=? ORDER BY seq",Integer.class,learner)).containsExactly(0,0);
        assertThat(cardStatus(first)).isEqualTo("USED");
        assertThat(cardStatus(second)).isEqualTo("USED");
    }

    @Test void voidAndRedeemNeverLeavePartialCardOrLedgerState() throws Exception {
        String raw=card("CREDIT",100,admin);
        String batch=jdbc.queryForObject("SELECT batch_id FROM learning.credit_cards WHERE id=?",String.class,cardId(raw));
        var voidCall=patch("/api/admin/card-batches/{batch}/void",batch).header("Authorization",bearer(admin,"ADMIN"));
        List<Outcome> results=concurrent(List.of(redeem(learner,raw),voidCall));
        assertThat(results.get(1).status()).isEqualTo(200);
        if(results.get(0).status()==200) {
            assertThat(cardStatus(raw)).isEqualTo("USED");
            assertThat(balance(learner)).isEqualTo(100);
            assertThat(mapper.readTree(results.get(1).body()).path("voided").asInt()).isZero();
        } else {
            assertThat(results.get(0).status()).isEqualTo(400);
            assertThat(mapper.readTree(results.get(0).body()).path("error_code").asText()).isEqualTo("INVALID_CARD_CODE");
            assertThat(cardStatus(raw)).isEqualTo("VOIDED");
            assertThat(balance(learner)).isZero();
            assertThat(mapper.readTree(results.get(1).body()).path("voided").asInt()).isEqualTo(1);
        }
    }

    private List<Outcome> concurrent(List<MockHttpServletRequestBuilder> calls) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(calls.size());
        CountDownLatch ready=new CountDownLatch(calls.size());
        CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures=new ArrayList<>();
            for(var call:calls) futures.add(pool.submit(()-> {
                ready.countDown();
                if(!start.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test start timed out.");
                var response=mvc.perform(call).andReturn().getResponse();
                return new Outcome(response.getStatus(),response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            }));
            assertThat(ready.await(15,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> results=new ArrayList<>();
            for(var future:futures) results.add(future.get(60,TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown(); pool.shutdownNow();
            assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();
        }
    }

    private record Outcome(int status,String body) {}
}

