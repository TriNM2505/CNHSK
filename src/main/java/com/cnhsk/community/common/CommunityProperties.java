package com.cnhsk.community.common;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.community")
public class CommunityProperties {

    @Min(1)
    private int postsPerDay = 5;

    @Min(1)
    private int commentsPerHour = 30;

    @Min(1)
    private int reportsPerDay = 20;

    @NotNull
    private Duration commentEditWindow = Duration.ofMinutes(15);

    public int getPostsPerDay() {
        return postsPerDay;
    }

    public void setPostsPerDay(int postsPerDay) {
        this.postsPerDay = postsPerDay;
    }

    public int getCommentsPerHour() {
        return commentsPerHour;
    }

    public void setCommentsPerHour(int commentsPerHour) {
        this.commentsPerHour = commentsPerHour;
    }

    public int getReportsPerDay() {
        return reportsPerDay;
    }

    public void setReportsPerDay(int reportsPerDay) {
        this.reportsPerDay = reportsPerDay;
    }

    public Duration getCommentEditWindow() {
        return commentEditWindow;
    }

    public void setCommentEditWindow(Duration commentEditWindow) {
        this.commentEditWindow = commentEditWindow;
    }

    @AssertTrue(message = "Community comment edit window must be positive")
    public boolean isCommentEditWindowValid() {
        return commentEditWindow != null && !commentEditWindow.isZero() && !commentEditWindow.isNegative();
    }
}
