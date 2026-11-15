package com.cnhsk.learning.quiz;

import java.time.Duration;
import java.util.Map;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Two sources share quiz policy; only the first source participates in the unique-first invariant. */
@Configuration(proxyBeanMethods = false)
public class QuizHandler {
    @Bean
    PracticeSourceHandler firstQuizHandler(QuizRepository repository, QuizConfig config, SettingsApi settings) {
        return new Source(PracticeSource.QUIZ, repository, config, settings);
    }

    @Bean
    PracticeSourceHandler retryQuizHandler(QuizRepository repository, QuizConfig config, SettingsApi settings) {
        return new Source(PracticeSource.QUIZ_RETRY, repository, config, settings);
    }

    private record Source(PracticeSource source, QuizRepository repository, QuizConfig config, SettingsApi settings)
            implements PracticeSourceHandler {
        @Override
        public SessionPolicy sessionPolicy() { return new SessionPolicy(Duration.ofMinutes(config.getSessionMinutes())); }

        @Override
        public SubmissionPolicy prepareSubmission(Attempt attempt) {
            if (source == PracticeSource.QUIZ) {
                repository.lockSubmission(attempt.getUserId(), attempt.getRefId());
                if (repository.hasFirstSubmission(attempt.getUserId(), attempt.getRefId(), attempt.getId())) {
                    attempt.convertQuizToRetry();
                }
            }
            return new SubmissionPolicy(100);
        }

        @Override
        public boolean isSuspicious(FinishContext context) {
            long minimum = (long) settings.getInt(SettingKey.QUIZ_MIN_SECONDS_PER_QUESTION) * 1000 * context.items().size();
            return Duration.between(context.servedAt(), context.submittedAt()).toMillis() < minimum;
        }

        @Override
        public Map<String, Object> onFinish(FinishContext context) {
            return Map.of("rankingEligible", context.source() == PracticeSource.QUIZ);
        }
    }
}
