package com.cnhsk.auth.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.cnhsk.auth.dto.RegisterRequest;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.audit.AuditService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates the first administrator only when explicit bootstrap credentials are configured. */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private static final String EMAIL_PROPERTY = "CNHSK_BOOTSTRAP_ADMIN_EMAIL";
    private static final String PASSWORD_PROPERTY = "CNHSK_BOOTSTRAP_ADMIN_PASSWORD";
    private static final String DISPLAY_NAME = "CNHSK Administrator";
    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private final Environment environment;
    private final AdminGuardService adminGuardService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;
    private final Validator validator;
    private final java.time.Clock clock;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private final TransactionTemplate transactionTemplate;

    public BootstrapAdminRunner(Environment environment, AdminGuardService adminGuardService,
            UserRepository userRepository, UserRoleRepository userRoleRepository,
            AuditService auditService, Validator validator, java.time.Clock clock,
            PlatformTransactionManager transactionManager) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null.");
        this.adminGuardService = Objects.requireNonNull(adminGuardService,
                "adminGuardService must not be null.");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository,
                "userRoleRepository must not be null.");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null.");
        this.validator = Objects.requireNonNull(validator, "validator must not be null.");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null."));
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public void run(ApplicationArguments args) {
        String emailValue = environment.getProperty(EMAIL_PROPERTY);
        String passwordValue = environment.getProperty(PASSWORD_PROPERTY);
        boolean hasEmail = hasValue(emailValue);
        boolean hasPassword = hasValue(passwordValue);

        if (!hasEmail && !hasPassword) {
            log.info("Bootstrap admin skipped: credentials are not configured.");
            return;
        }
        if (hasEmail != hasPassword) {
            throw new IllegalStateException("Bootstrap admin requires both credential variables.");
        }

        try {
            BootstrapResult result = transactionTemplate.execute(status -> bootstrap(
                    emailValue.strip().toLowerCase(Locale.ROOT), passwordValue));
            if (result == BootstrapResult.CREATED) {
                log.info("Bootstrap admin initialized.");
            } else {
                log.info("Bootstrap admin skipped: an ADMIN role already exists.");
            }
        } catch (RuntimeException failure) {
            // Avoid propagating database messages that may contain credential-related values to startup logs.
            log.error("Bootstrap admin initialization failed; cause_type={}",
                    failure.getClass().getSimpleName());
            throw new IllegalStateException("Bootstrap admin initialization failed.");
        }
    }

    private BootstrapResult bootstrap(String email, String password) {
        // Acquires the same transaction-scoped lock used by role grant/revoke and ban operations.
        adminGuardService.lockAndCountActiveAdmins();
        if (userRoleRepository.countByRoleCode(RoleCode.ADMIN) > 0) {
            return BootstrapResult.ALREADY_CONFIGURED;
        }

        validateCredentials(email, password);
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Bootstrap admin email is already in use.");
        }

        User user = new User(email, passwordEncoder.encode(password), DISPLAY_NAME);
        user.setEmailVerifiedAt(clock.instant());
        User savedUser = userRepository.saveAndFlush(user);
        userRoleRepository.saveAllAndFlush(List.of(
                new UserRole(savedUser.getId(), RoleCode.ADMIN, null),
                new UserRole(savedUser.getId(), RoleCode.LEARNER, null)));
        auditService.record(null, "BOOTSTRAP_ADMIN", "user:" + savedUser.getId(), null,
                List.of(RoleCode.ADMIN.name(), RoleCode.LEARNER.name()));
        return BootstrapResult.CREATED;
    }

    private void validateCredentials(String email, String password) {
        Set<ConstraintViolation<RegisterRequest>> emailViolations = validator.validateValue(
                RegisterRequest.class, "email", email);
        Set<ConstraintViolation<RegisterRequest>> passwordViolations = validator.validateValue(
                RegisterRequest.class, "password", password);
        boolean passwordPolicyValid = password.length() >= 8 && password.length() <= 72
                && LETTER.matcher(password).find()
                && DIGIT.matcher(password).find()
                && password.equals(password.strip())
                && !password.equalsIgnoreCase(email);
        if (!emailViolations.isEmpty() || !passwordViolations.isEmpty() || !passwordPolicyValid) {
            throw new IllegalStateException("Bootstrap credentials do not satisfy registration policy.");
        }
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private enum BootstrapResult {
        CREATED,
        ALREADY_CONFIGURED
    }
}
