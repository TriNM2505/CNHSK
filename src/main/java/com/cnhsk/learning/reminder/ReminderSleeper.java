package com.cnhsk.learning.reminder;

import java.time.Duration;

import org.springframework.stereotype.Component;

/** Injectable retry delay so reminder tests do not wait on wall-clock time. */
@Component
public class ReminderSleeper {

    public void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }
}
