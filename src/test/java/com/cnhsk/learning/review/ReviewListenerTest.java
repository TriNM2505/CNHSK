package com.cnhsk.learning.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cnhsk.learning.practice.PracticeSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

class ReviewListenerTest {

    @Test
    void topicStudyFirstRatingPrefersLearnedWhenAnswersAreMixed() {
        assertThat(ReviewListener.firstRating(PracticeSource.TOPIC_STUDY, true, true, false))
                .isEqualTo(FsrsScheduler.Rating.GOOD);
        assertThat(ReviewListener.firstRating(PracticeSource.TOPIC_STUDY, false, true, false))
                .isEqualTo(FsrsScheduler.Rating.EASY);
    }

    @Test
    void gradedFirstAndReviewUseAnyValidWrongAnswer() {
        assertThat(ReviewListener.firstRating(PracticeSource.TOPIC_PRACTICE, false, false, false))
                .isEqualTo(FsrsScheduler.Rating.GOOD);
        assertThat(ReviewListener.firstRating(PracticeSource.TOPIC_PRACTICE, false, false, true))
                .isEqualTo(FsrsScheduler.Rating.AGAIN);
        assertThat(ReviewListener.reviewRating(PracticeSource.TOPIC_PRACTICE, true))
                .isEqualTo(FsrsScheduler.Rating.AGAIN);
        assertThat(ReviewListener.reviewRating(PracticeSource.TOPIC_STUDY, true))
                .isEqualTo(FsrsScheduler.Rating.GOOD);
    }

    @Test
    void onlyApprovedSourceWhitelistIsHandled() {
        assertThat(List.of(PracticeSource.TOPIC_STUDY, PracticeSource.TOPIC_PRACTICE,
                PracticeSource.TOPIC_TEST, PracticeSource.GRAMMAR_LESSON, PracticeSource.CHAR_RECOGNITION,
                PracticeSource.GAME_ROUND, PracticeSource.MOCK_EXAM, PracticeSource.PLACEMENT,
                PracticeSource.TARGETED_PRACTICE).stream().allMatch(ReviewListener::supports)).isTrue();
        assertThat(ReviewListener.supports(PracticeSource.CHAR_WRITING)).isFalse();
        assertThat(ReviewListener.supports(PracticeSource.WRITING_CHALLENGE)).isFalse();
        assertThat(ReviewListener.supports(PracticeSource.PRON_STAGE)).isFalse();
    }

    @Test
    void listenerRunsBeforeMasteryListener() throws NoSuchMethodException {
        Order order = ReviewListener.class.getMethod("onFinished",
                com.cnhsk.learning.practice.event.SessionFinishedEvent.class).getAnnotation(Order.class);
        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
