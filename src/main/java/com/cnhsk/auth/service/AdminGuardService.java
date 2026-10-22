package com.cnhsk.auth.service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

import com.cnhsk.auth.repository.UserRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes changes that can affect the count of active administrators. */
@Service
public class AdminGuardService {

    public static final int LOCK_NAMESPACE = 1_129_203_795;
    public static final int ADMIN_MEMBERSHIP_LOCK_ID = 1;

    private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    public AdminGuardService(JdbcTemplate jdbcTemplate, UserRepository userRepository) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null.");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long lockAndCountActiveAdmins() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(LOCK_SQL)) {
                statement.setInt(1, LOCK_NAMESPACE);
                statement.setInt(2, ADMIN_MEMBERSHIP_LOCK_ID);
                statement.execute();
            } catch (SQLException exception) {
                throw exception;
            }
            return null;
        });
        return userRepository.countActiveAdmins();
    }
}
