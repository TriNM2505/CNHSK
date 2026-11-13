package com.cnhsk.auth.reminder;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.ReminderSettings;
import com.cnhsk.auth.dto.ReminderSettingsResponse;
import com.cnhsk.auth.dto.ReminderSettingsResponse.Channels;
import com.cnhsk.auth.dto.ReminderUpdateRequest;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads defaults without persisting them and updates reminder settings atomically. */
@Service
public class ReminderSettingsService {

    private static final LocalTime DEFAULT_TIME = LocalTime.of(20, 0);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String TIMEZONE = "Asia/Ho_Chi_Minh";

    private final UserRepository userRepository;
    private final AuditService auditService;

    public ReminderSettingsService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ReminderSettingsResponse getSettings(long userId) {
        return toResponse(readSettings(userId));
    }

    @Transactional(readOnly = true)
    public ReminderSettings readSettings(long userId) {
        return settingsOf(userRepository.findById(userId).orElseThrow(ReminderSettingsService::unauthenticated));
    }

    @Transactional
    public ReminderSettingsResponse updateSettings(long userId, ReminderUpdateRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(ReminderSettingsService::unauthenticated);
        if (request.email() && user.getEmailVerifiedAt() == null) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Vui lòng xác thực email trước khi bật nhắc qua email.", Map.of());
        }

        ReminderSettingsResponse before = toResponse(settingsOf(user));
        user.setReminderTime(LocalTime.parse(request.reminderTime()));
        user.setReminderChannels(canonicalChannels(request));
        ReminderSettingsResponse after = toResponse(settingsOf(user));
        auditService.record(userId, "REMINDER_SETTINGS_UPDATED", "user:" + userId,
                auditSettings(before), auditSettings(after));
        return after;
    }

    private static ReminderSettings settingsOf(User user) {
        boolean configured = user.getReminderTime() != null;
        LocalTime reminderTime = configured ? user.getReminderTime() : DEFAULT_TIME;
        List<String> channels = List.of(user.getReminderChannels().split(","));
        return new ReminderSettings(reminderTime, !configured || channels.contains("WEB"),
                configured && channels.contains("EMAIL"), user.getEmailVerifiedAt() != null, configured);
    }

    private static ReminderSettingsResponse toResponse(ReminderSettings settings) {
        return new ReminderSettingsResponse(TIME_FORMAT.format(settings.reminderTime()),
                new Channels(settings.web(), settings.email()), settings.emailVerified(), settings.configured(), TIMEZONE);
    }

    private static Map<String, Object> auditSettings(ReminderSettingsResponse settings) {
        return Map.of("reminderTime", settings.reminderTime(), "channels", settings.channels(),
                "configured", settings.configured());
    }

    private static String canonicalChannels(ReminderUpdateRequest request) {
        if (request.web() && request.email()) {
            return "WEB,EMAIL";
        }
        if (request.web()) {
            return "WEB";
        }
        return request.email() ? "EMAIL" : "";
    }

    private static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
                "Không thể xác thực người dùng hiện tại.", Map.of());
    }
}
