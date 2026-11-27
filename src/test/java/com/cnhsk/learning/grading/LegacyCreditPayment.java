package com.cnhsk.learning.grading;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Points are no longer spent on new requests, but requests paid with points before that change still
 * exist. Tests submit a request on the free allowance and then turn it into such a point-paid request,
 * so refunds and payouts of old requests stay covered.
 */
public final class LegacyCreditPayment {
    private LegacyCreditPayment() {
    }

    /** Records a one-point debit for the request and marks its receipt as paid with points. */
    public static long payWithPoint(JdbcTemplate jdbc, long userId, long requestId) {
        int before = jdbc.queryForList("SELECT balance_after FROM learning.credit_transactions WHERE user_id=? "
                + "ORDER BY seq DESC LIMIT 1", Integer.class, userId).stream().findFirst().orElse(0);
        if (before < 1) {
            throw new IllegalStateException("Top up the learner before paying with a point");
        }
        Long debit = jdbc.queryForObject("""
                INSERT INTO learning.credit_transactions(user_id,seq,tx_type,amount,balance_before,balance_after,
                    feature_code)
                VALUES (?,(SELECT COALESCE(MAX(seq),0)+1 FROM learning.credit_transactions WHERE user_id=?),
                    'DEDUCT',-1,?,?,'GRADING')
                RETURNING id
                """, Long.class, userId, userId, before, before - 1);
        jdbc.update("""
                UPDATE learning.service_requests
                SET payload = jsonb_set(jsonb_set(payload, '{consumption,source}', '"CREDIT"'),
                    '{consumption,transactionId}', to_jsonb(?::bigint))
                WHERE id=?
                """, debit, requestId);
        return debit;
    }
}
