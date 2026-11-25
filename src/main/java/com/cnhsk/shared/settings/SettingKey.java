package com.cnhsk.shared.settings;

public enum SettingKey {
    BILLING_FREE_MONTHLY_LIMIT(1, "billing.free-monthly-limit", "Thanh toán", "Lượt miễn phí mỗi tháng", 0, 1_000),
    ASSISTANT_DAILY_LIMIT(2, "assistant.daily-limit", "Trợ lý & AI", "Lượt trợ lý mỗi ngày", 0, 10_000),
    AI_MONTHLY_TOKEN_BUDGET(3, "ai.monthly-token-budget", "Trợ lý & AI", "Ngân sách token AI mỗi tháng", 0, 100_000_000),
    LIBRARY_GUEST_LOOKUPS_PER_HOUR(4, "library.guest-lookups-per-hour", "Thư viện", "Lượt gọi từ điển của khách mỗi giờ, mỗi địa chỉ mạng", 1, 10_000),
    LIBRARY_AUTHENTICATED_LOOKUPS_PER_HOUR(5, "library.authenticated-lookups-per-hour", "Thư viện", "Lượt tra cứu của thành viên mỗi giờ", 1, 10_000),
    LIBRARY_MAX_NOTES(6, "library.max-notes", "Thư viện", "Số ghi chú tối đa", 1, 10_000),
    LIBRARY_MAX_DECKS(7, "library.max-decks", "Thư viện", "Số bộ thẻ tối đa", 1, 500),
    LIBRARY_MAX_CARDS(8, "library.max-cards", "Thư viện", "Số thẻ tối đa mỗi bộ", 1, 5_000),
    LIBRARY_FLASHCARD_REVIEWS_PER_HOUR(9, "library.flashcard-reviews-per-hour", "Thư viện", "Lượt ôn thẻ mỗi giờ", 1, 10_000),
    QUIZ_QUESTION_COUNT(10, "quiz.question-count", "Quiz", "Số câu hỏi mỗi lượt quiz", 5, 30),
    QUIZ_MIN_SECONDS_PER_QUESTION(11, "quiz.min-seconds-per-question", "Quiz", "Thời gian tối thiểu mỗi câu quiz (giây)", 0, 60),
    GRADING_CLAIM_HOURS(12, "grading.claim-hours", "Chấm bài", "Thời hạn nhận bài (giờ)", 1, 168),
    GRADING_GRADING_HOURS(13, "grading.grading-hours", "Chấm bài", "Thời hạn chấm bài (giờ)", 1, 168),
    GRADING_TEACHER_PAYOUT_CREDITS(14, "grading.teacher-payout-credits", "Chấm bài", "Điểm trả cho giáo viên mỗi bài", 0, 10),
    GRADING_MIN_HANZI(15, "grading.min-hanzi", "Chấm bài", "Số chữ Hán tối thiểu", 1, 1_000),
    GRADING_MAX_HANZI(16, "grading.max-hanzi", "Chấm bài", "Số chữ Hán tối đa", 10, 2_000),
    GRADING_MAX_ACTIVE_CLAIMS(17, "grading.max-active-claims", "Chấm bài", "Số bài đang nhận tối đa", 1, 20),
    CONTEST_SUBMIT_GRACE_SECONDS(18, "contest.submit-grace-seconds", "Cuộc thi", "Thời gian gia hạn nộp bài (giây)", 0, 60),
    AI_GENERATE_SYSTEM_USER_ID(19, "ai-generate.system-user-id", "AI sinh câu", "Mã tài khoản quản trị đứng tên lô, 0 để tắt tác vụ", 0, Integer.MAX_VALUE),
    AI_GENERATE_WINDOW_DAYS(20, "ai-generate.window-days", "AI sinh câu", "Số ngày xét câu trả lời sai", 1, 90),
    AI_GENERATE_MIN_WRONG(21, "ai-generate.min-wrong", "AI sinh câu", "Số lượt sai tối thiểu của một điểm tri thức", 1, 1_000),
    AI_GENERATE_MIN_BANK(22, "ai-generate.min-bank", "AI sinh câu", "Chỉ sinh khi số câu đã duyệt của điểm tri thức ít hơn ngưỡng này", 1, 200),
    AI_GENERATE_BATCH_SIZE(23, "ai-generate.batch-size", "AI sinh câu", "Số câu hỏi mỗi lô", 1, 10),
    AI_GENERATE_MAX_BATCHES_PER_RUN(24, "ai-generate.max-batches-per-run", "AI sinh câu", "Số lô tối đa mỗi lần chạy", 0, 20),
    LEARNING_FREE_MONTHLY_LIMIT(25, "learning.free-monthly-limit", "Học tập", "Lượt học miễn phí mỗi tháng cho mỗi tính năng", 0, 1_000),
    AI_GENERATE_DAILY_CAP(26, "ai-generate.daily-cap", "AI sinh câu", "Số câu AI tối đa được lưu mỗi ngày, 0 để không sinh", 0, 500),
    AI_GENERATE_SEMANTIC_THRESHOLD(27, "ai-generate.semantic-threshold", "AI sinh câu", "Ngưỡng giống nghĩa (phần nghìn) để bỏ câu trùng cùng đáp án", 500, 1_000),
    AI_GENERATE_MAX_PENDING(28, "ai-generate.max-pending", "AI sinh câu", "Số câu AI chờ duyệt tối đa; đạt ngưỡng thì tạm không sinh, 0 để không giới hạn", 0, 10_000);

    // Audit IDs are permanent identifiers: never reuse an ID, including after removing a key.
    private final long auditId;
    private final String key;
    private final String group;
    private final String description;
    private final int min;
    private final int max;

    SettingKey(long auditId, String key, String group, String description, int min, int max) {
        this.auditId = auditId;
        this.key = key;
        this.group = group;
        this.description = description;
        this.min = min;
        this.max = max;
    }

    public long auditId() { return auditId; }
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
