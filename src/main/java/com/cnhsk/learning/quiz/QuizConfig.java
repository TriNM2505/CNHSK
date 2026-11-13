package com.cnhsk.learning.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.quiz")
public class QuizConfig {
    @Min(5)
    @Max(30)
    private int questionCount = 10;

    @Min(0)
    @Max(10)
    private int minSecondsPerQuestion = 2;

    @Positive
    private int sessionMinutes = 30;

    public int getQuestionCount() { return questionCount; }
    public void setQuestionCount(int questionCount) { this.questionCount = questionCount; }
    public int getMinSecondsPerQuestion() { return minSecondsPerQuestion; }
    public void setMinSecondsPerQuestion(int minSecondsPerQuestion) { this.minSecondsPerQuestion = minSecondsPerQuestion; }
    public int getSessionMinutes() { return sessionMinutes; }
    public void setSessionMinutes(int sessionMinutes) { this.sessionMinutes = sessionMinutes; }
}
