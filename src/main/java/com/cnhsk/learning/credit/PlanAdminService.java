package com.cnhsk.learning.credit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.credit.web.Benefits;
import com.cnhsk.learning.credit.web.PlanStatus.PlanCode;
import com.cnhsk.learning.credit.web.PlanUpdateRequest;
import com.cnhsk.learning.credit.web.PlanView;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanAdminService {
    private static final String COLUMNS = "code,name,description,duration_days,price,benefits::text,is_active";
    private static final List<PlanCode> PLAN_CODES = List.of(PlanCode.FREE, PlanCode.PREMIUM, PlanCode.PREMIUM_PLUS);
    private static final TypeReference<Map<String, Integer>> BENEFITS_TYPE = new TypeReference<>() { };

    private final AuthApi auth;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    public PlanAdminService(AuthApi auth, NamedParameterJdbcTemplate jdbc, ObjectMapper mapper,
            AuditService audit, Clock clock) {
        this.auth = auth;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<PlanView> listPlans() {
        admin();
        Map<PlanCode, Catalog> catalog = new EnumMap<>(PlanCode.class);
        jdbc.query("SELECT " + COLUMNS + " FROM learning.plans", Map.of(), this::row)
                .forEach(plan -> catalog.put(plan.code(), plan));
        Instant now = clock.instant();
        return PLAN_CODES.stream().map(code -> {
            long activeUsers = auth.countActivePlan(code.name(), now);
            Catalog plan = catalog.get(code);
            return plan == null
                    ? new PlanView(code, false, null, null, null, null, new Benefits(), null, activeUsers)
                    : view(plan, activeUsers);
        }).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanView updatePlan(PlanCode code, PlanUpdateRequest request) {
        long actor = admin();
        if (code == null || request == null) throw PlanUpdateRequest.invalid();
        request.validate(code);
        if (code == PlanCode.PREMIUM_PLUS) throw unavailable();

        Catalog before = lockedPlan(code).orElse(null);
        if (before == null && code == PlanCode.FREE) throw unavailable();
        Catalog after = before;
        boolean created = false;
        if (before == null) {
            created = jdbc.update("""
                    INSERT INTO learning.plans (code,name,description,duration_days,price,benefits,is_active)
                    VALUES (:code,:name,:description,:duration,:price,CAST(:benefits AS jsonb),:active)
                    ON CONFLICT (code) DO NOTHING
                    """, parameters(code, request)) == 1;
            after = lockedPlan(code).orElseThrow(PlanAdminService::internal);
            if (!created) before = after;
        }

        Instant now = clock.instant();
        // Không khóa users để tránh vòng khóa với redeem.
        long activeUsers = auth.countActivePlan(code.name(), now);
        if (!created && code == PlanCode.PREMIUM && activeUsers > 0
                && benefitsChanged(before.benefits(), request.benefits())) {
            throw new ApiException(ErrorCode.PLAN_IN_USE, HttpStatus.CONFLICT,
                    "Không thể thay đổi quyền lợi của gói đang có người sử dụng.", Map.of());
        }

        if (!created) {
            String sql = code == PlanCode.FREE
                    ? "UPDATE learning.plans SET name=:name,description=:description WHERE code=:code RETURNING " + COLUMNS
                    : """
                            UPDATE learning.plans SET name=:name,description=:description,duration_days=:duration,
                                   price=:price,benefits=CAST(:benefits AS jsonb),is_active=:active
                            WHERE code=:code RETURNING
                            """ + COLUMNS;
            after = jdbc.query(sql, parameters(code, request), this::row)
                    .stream().findFirst().orElseThrow(PlanAdminService::internal);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", code.name());
        detail.put("before", before == null ? null : snapshot(before));
        detail.put("after", snapshot(after));
        audit.recordDetail(actor, created ? "PLAN_CREATED" : "PLAN_UPDATED", "PLAN", detail);
        return view(after, activeUsers);
    }

    private Optional<Catalog> lockedPlan(PlanCode code) {
        return jdbc.query("SELECT " + COLUMNS + " FROM learning.plans WHERE code=:code FOR NO KEY UPDATE",
                Map.of("code", code.name()), this::row).stream().findFirst();
    }

    private MapSqlParameterSource parameters(PlanCode code, PlanUpdateRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("code", code.name())
                .addValue("name", request.name().trim()).addValue("description", request.description());
        if (code == PlanCode.PREMIUM) {
            parameters.addValue("duration", request.durationDays())
                    .addValue("price", new BigDecimal(request.price()).setScale(2, RoundingMode.UNNECESSARY))
                    .addValue("benefits", benefitsJson(request.benefits())).addValue("active", request.isActive());
        }
        return parameters;
    }

    private Catalog row(ResultSet rs, int index) throws SQLException {
        try {
            Benefits benefits = new Benefits(mapper.readValue(rs.getString("benefits"), BENEFITS_TYPE));
            return new Catalog(PlanCode.valueOf(rs.getString("code")), rs.getString("name"),
                    rs.getString("description"), rs.getObject("duration_days", Integer.class),
                    rs.getBigDecimal("price"), benefits, rs.getBoolean("is_active"));
        } catch (JsonProcessingException exception) {
            throw internal();
        }
    }

    private String benefitsJson(Benefits benefits) {
        try {
            return mapper.writeValueAsString(benefits);
        } catch (JsonProcessingException exception) {
            throw internal();
        }
    }

    private static boolean benefitsChanged(Benefits before, Benefits after) {
        for (PaidFeature feature : PaidFeature.values()) {
            if (!before.getOrDefault(feature.name(), 0).equals(after.getOrDefault(feature.name(), 0))) return true;
        }
        return false;
    }

    private static PlanView view(Catalog plan, long activeUsers) {
        return new PlanView(plan.code(), true, plan.name(), plan.description(), plan.durationDays(),
                price(plan.price()), new Benefits(plan.benefits()), plan.isActive(), activeUsers);
    }

    private static Map<String, Object> snapshot(Catalog plan) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", plan.name());
        snapshot.put("description", plan.description());
        snapshot.put("durationDays", plan.durationDays());
        snapshot.put("price", price(plan.price()));
        snapshot.put("benefits", new Benefits(plan.benefits()));
        snapshot.put("isActive", plan.isActive());
        return snapshot;
    }

    private static String price(BigDecimal price) {
        return price.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private long admin() {
        var current = auth.currentUser();
        if (!current.roles().contains(RoleCode.ADMIN)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        return current.userId();
    }

    private static ApiException unavailable() {
        return new ApiException(ErrorCode.PLAN_NOT_AVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY,
                "Gói dịch vụ hiện không khả dụng.", Map.of());
    }

    private static ApiException internal() {
        return new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi nội bộ.", Map.of());
    }

    private record Catalog(PlanCode code, String name, String description, Integer durationDays,
                           BigDecimal price, Benefits benefits, boolean isActive) { }
}
