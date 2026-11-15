package com.cnhsk.learning.credit;

import java.time.Clock;

import com.cnhsk.auth.api.AuthApi;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps stored plan status aligned with expiry without changing quota or wallet history. */
@Component
public class PlanMaintenance {

    private final AuthApi auth;
    private final Clock clock;

    public PlanMaintenance(AuthApi auth, Clock clock) {
        this.auth = auth;
        this.clock = clock;
    }

    @Scheduled(cron = "${cnhsk.billing.downgrade-cron:0 10 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void downgradeExpiredPlans() {
        auth.downgradeExpiredPlans(clock.instant());
    }
}
