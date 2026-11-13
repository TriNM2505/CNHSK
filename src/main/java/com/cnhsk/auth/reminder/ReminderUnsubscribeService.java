package com.cnhsk.auth.reminder;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.shared.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderUnsubscribeService {

    private final UnsubscribeTokens tokens;
    private final UserRepository users;
    private final AuditService audit;

    public ReminderUnsubscribeService(UnsubscribeTokens tokens, UserRepository users, AuditService audit) {
        this.tokens = tokens;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public void unsubscribe(String token) {
        long userId = tokens.verify(token);
        User user = users.findByIdForUpdate(userId).orElseThrow(UnsubscribeTokens::invalid);
        String before = user.getReminderChannels();
        List<String> channels = List.of(before.split(","));
        if (!channels.contains("EMAIL")) {
            return;
        }
        String after = channels.contains("WEB") ? "WEB" : "";
        user.setReminderChannels(after);
        audit.record(userId, "REMINDER_EMAIL_UNSUBSCRIBED", "user:" + userId,
                Map.of("channels", before), Map.of("channels", after));
    }
}
