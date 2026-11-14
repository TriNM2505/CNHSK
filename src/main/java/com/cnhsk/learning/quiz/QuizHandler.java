package com.cnhsk.learning.quiz;

import java.time.Duration;
import java.util.Map;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.entity.Attempt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Two sources share quiz policy; only the first source participates in the unique-first invariant. */
@Configuration(proxyBeanMethods = false)
public class QuizHandler {
    @Bean
    PracticeSourceHandler firstQuizHandler(QuizRepository repository, QuizConfig config) {
        return new Source(PracticeSource.QUIZ, repository, config);
    }

    @Bean
    PracticeSourceHandler retryQuizHandler(QuizRepository repository, QuizConfig config) {
        return new Source(PracticeSource.QUIZ_RETRY, repository, config);
    }

    private record Source(PracticeSource source, QuizRepository repository, QuizConfig config)
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
            long minimum = (long) config.getMinSecondsPerQuestion() * 1000 * context.items().size();
            return Duration.between(context.servedAt(), context.submittedAt()).toMillis() < minimum;
        }

        @Override
        public Map<String, Object> onFinish(FinishContext context) {
            return Map.of("rankingEligible", context.source() == PracticeSource.QUIZ);
        }
    }
}
