package com.cnhsk.shared.settings;

public enum SettingKey {
    BILLING_FREE_MONTHLY_LIMIT("billing.free-monthly-limit", "Thanh toán", "Lượt miễn phí mỗi tháng", 0, 1_000),
    ASSISTANT_DAILY_LIMIT("assistant.daily-limit", "Trợ lý & AI", "Lượt trợ lý mỗi ngày", 0, 10_000),
    AI_MONTHLY_TOKEN_BUDGET("ai.monthly-token-budget", "Trợ lý & AI", "Ngân sách token AI mỗi tháng", 0, 100_000_000),
    LIBRARY_GUEST_LOOKUPS_PER_HOUR("library.guest-lookups-per-hour", "Thư viện", "Lượt tra cứu của khách mỗi giờ", 1, 10_000),
    LIBRARY_AUTHENTICATED_LOOKUPS_PER_HOUR("library.authenticated-lookups-per-hour", "Thư viện", "Lượt tra cứu của thành viên mỗi giờ", 1, 10_000),
    LIBRARY_MAX_NOTES("library.max-notes", "Thư viện", "Số ghi chú tối đa", 1, 10_000),
    LIBRARY_MAX_DECKS("library.max-decks", "Thư viện", "Số bộ thẻ tối đa", 1, 500),
    LIBRARY_MAX_CARDS("library.max-cards", "Thư viện", "Số thẻ tối đa mỗi bộ", 1, 5_000),
    LIBRARY_FLASHCARD_REVIEWS_PER_HOUR("library.flashcard-reviews-per-hour", "Thư viện", "Lượt ôn thẻ mỗi giờ", 1, 10_000),
    QUIZ_QUESTION_COUNT("quiz.question-count", "Quiz", "Số câu hỏi mỗi lượt quiz", 5, 30),
    QUIZ_MIN_SECONDS_PER_QUESTION("quiz.min-seconds-per-question", "Quiz", "Thời gian tối thiểu mỗi câu quiz (giây)", 0, 60),
    GRADING_CLAIM_HOURS("grading.claim-hours", "Chấm bài", "Thời hạn nhận bài (giờ)", 1, 168),
    GRADING_GRADING_HOURS("grading.grading-hours", "Chấm bài", "Thời hạn chấm bài (giờ)", 1, 168),
    GRADING_TEACHER_PAYOUT_CREDITS("grading.teacher-payout-credits", "Chấm bài", "Điểm trả cho giáo viên mỗi bài", 0, 10),
    GRADING_MIN_HANZI("grading.min-hanzi", "Chấm bài", "Số chữ Hán tối thiểu", 1, 1_000),
    GRADING_MAX_HANZI("grading.max-hanzi", "Chấm bài", "Số chữ Hán tối đa", 10, 2_000),
    GRADING_MAX_ACTIVE_CLAIMS("grading.max-active-claims", "Chấm bài", "Số bài đang nhận tối đa", 1, 20),
    CONTEST_SUBMIT_GRACE_SECONDS("contest.submit-grace-seconds", "Cuộc thi", "Thời gian gia hạn nộp bài (giây)", 0, 60);

    private final String key;
    private final String group;
    private final String description;
    private final int min;
    private final int max;

    SettingKey(String key, String group, String description, int min, int max) {
        this.key = key;
        this.group = group;
        this.description = description;
        this.min = min;
        this.max = max;
    }

    public String key() { return key; }
    public String group() { return group; }
    public String description() { return description; }
    public String type() { return "INT"; }
    public int min() { return min; }
    public int max() { return max; }
    public String defaultProperty() { return "cnhsk." + key; }

    public boolean accepts(int value) {
        return value >= min && value <= max;
    }
}
