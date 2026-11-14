package com.cnhsk.shared.settings;

import java.time.Clock;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SettingsService implements SettingsApi {
    private static final Logger LOG = LoggerFactory.getLogger(SettingsService.class);
    private static final long CACHE_MILLIS = 30_000;
    private static final long WARN_MILLIS = 60_000;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate outsideCaller;
    private final Slot[] slots = new Slot[SettingKey.values().length];
    private Long nextWarningAt;

    public SettingsService(JdbcTemplate jdbc, Environment environment, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.outsideCaller = new TransactionTemplate(transactionManager);
        outsideCaller.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        for (SettingKey key : SettingKey.values()) {
            String raw = environment.getProperty(key.defaultProperty());
            try {
                int value = Integer.parseInt(raw);
                if (!key.accepts(value)) {
                    throw new NumberFormatException();
                }
                slots[key.ordinal()] = new Slot(value);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Invalid or missing default for setting " + key.key());
            }
        }
    }

    @Override
    public int getInt(SettingKey key) {
        Slot slot = slots[Objects.requireNonNull(key).ordinal()];
        CacheEntry entry = slot.cached;
        if (entry != null && clock.millis() < entry.expiresAt()) {
            return entry.value();
        }
        synchronized (slot) {
            entry = slot.cached;
            if (entry == null || clock.millis() >= entry.expiresAt()) {
                int value = load(key, slot.defaultValue);
                entry = new CacheEntry(value, clock.millis() + CACHE_MILLIS);
                slot.cached = entry;
            }
            return entry.value();
        }
    }

    // Call after a successful write transaction commits, including resets.
    void invalidate(SettingKey key) {
        Slot slot = slots[Objects.requireNonNull(key).ordinal()];
        synchronized (slot) {
            slot.cached = null;
        }
    }

    private int load(SettingKey key, int defaultValue) {
        try {
            String raw = outsideCaller.execute(status -> jdbc.query(
                    "SELECT value FROM shared.app_settings WHERE key = ?",
                    result -> result.next() ? result.getString(1) : null, key.key()));
            if (raw == null) {
                return defaultValue;
            }
            int value = Integer.parseInt(raw);
            if (key.accepts(value)) {
                return value;
            }
        } catch (DataAccessException | NumberFormatException exception) {
            // Never log database values, exception messages or connection details.
        }
        warnUnavailable();
        return defaultValue;
    }

    private synchronized void warnUnavailable() {
        long now = clock.millis();
        if (nextWarningAt == null || now >= nextWarningAt) {
            LOG.warn("Settings override unavailable or invalid; using configured default");
            nextWarningAt = now + WARN_MILLIS;
        }
    }

    private static final class Slot {
        private final int defaultValue;
        private volatile CacheEntry cached;

        private Slot(int defaultValue) {
            this.defaultValue = defaultValue;
        }
    }

    private record CacheEntry(int value, long expiresAt) { }
}
