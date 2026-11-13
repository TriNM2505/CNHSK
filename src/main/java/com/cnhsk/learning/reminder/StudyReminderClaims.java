package com.cnhsk.learning.reminder;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Commits each claim before SMTP and releases only the row owned by that attempt. */
@Component
public class StudyReminderClaims {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public StudyReminderClaims(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public OptionalLong claim(long userId, LocalDate localDate) {
        return transactions.execute(status -> {
            List<Long> ids = jdbc.query("""
                    INSERT INTO learning.study_events(user_id, event_type, channel, local_date)
                    VALUES (:userId, 'REMINDER_SENT', 'EMAIL', :localDate)
                    ON CONFLICT DO NOTHING
                    RETURNING id
                    """, new MapSqlParameterSource("userId", userId).addValue("localDate", localDate, Types.DATE),
                    (row, index) -> row.getLong("id"));
            return ids.isEmpty() ? OptionalLong.empty() : OptionalLong.of(ids.get(0));
        });
    }

    public void release(long claimId) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                DELETE FROM learning.study_events
                WHERE id = :id AND event_type = 'REMINDER_SENT' AND channel = 'EMAIL'
                """, Map.of("id", claimId)));
    }
}
