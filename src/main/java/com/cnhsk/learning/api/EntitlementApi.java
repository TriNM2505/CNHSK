package com.cnhsk.learning.api;

/**
 * Billing boundary for paid operations. Both methods require an existing writable
 * transaction. Use the authenticated user's ID and retain the server-issued receipt;
 * never accept a receipt reconstructed from client input.
 *
 * <p>Call consume in a short transaction, wait for its commit, then call the external
 * provider outside any transaction so the user lock is released. On provider failure,
 * call refund in a new transaction. A translation cache hit must not consume.
 * The system assistant does not use this billing API.
 *
 * <p>One user/key identifies one consumption, including after refund. A retry that
 * needs a new charge must use a new key; refund never invalidates the original receipt.
 * Redis receipts live for 24 hours and are best effort when Redis is unavailable.
 */
public interface EntitlementApi {

    Consumption consume(long userId, PaidFeature feature, String idempotencyKey);

    void refund(long userId, Consumption consumption);
}
