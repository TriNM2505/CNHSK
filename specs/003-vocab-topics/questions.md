# Câu hỏi và quyết định — Spec 003

## Q-T02-01 — Biểu diễn TTL động của GAME_ROUND trong PracticeSource

**Task:** T-02. **Trạng thái:** Đã chốt bởi leader ngày 2026-10-30.

**Căn cứ:**

- Spec 003 §0.2 yêu cầu `Duration ttl()` trên enum `PracticeSource`; TTL của `GAME_ROUND` là "theo game".
- Spec 003 §0.11 có `StartPracticeCommand.ttlOverride`.
- Spec 007 FR-009 yêu cầu game truyền `ttlOverride = 3 giây + timeLimitSeconds + 15 giây`.
- `WRITING_CHALLENGE` đã có TTL cố định 210 giây theo spec 006 §0 và FR-021; không cần hỏi lại giá trị này.

**Điểm chưa được đặc tả:** giá trị trả về hoặc hành vi của `GAME_ROUND.ttl()` khi không có ngữ cảnh game. Không thể chọn một thời lượng cố định đúng cho mọi game.

**Quyết định đã duyệt:** Giữ chữ ký `Duration ttl()` theo spec 003 và chọn `null` cho `GAME_ROUND`, dùng nhất quán; không dùng `Optional<Duration>`.

- `GAME_ROUND` không có TTL mặc định. `PracticeService.start` ở T-03 bắt buộc nhận `ttlOverride > 0`; thiếu, bằng 0 hoặc âm đều ném `IllegalArgumentException` vì là lỗi lập trình, không tự chọn hạn mặc định.
- Tám nguồn còn lại có TTL cố định, gồm `WRITING_CHALLENGE = 210 giây`. Truyền bất kỳ `ttlOverride` nào cho nguồn cố định đều ném `IllegalArgumentException`, kể cả giá trị bằng TTL cố định.
- T-02 chỉ khai báo và kiểm thử metadata của chín nguồn, không triển khai `PracticeService.start`.
- T-03 phải kiểm thử: game thiếu/không có override dương bị từ chối; game có override dương tính đúng hạn; nguồn cố định có override bị từ chối.

## Ghi chú kiểm chứng contract — báo leader, ngoài phạm vi sửa của T-02

Khi kiểm chứng bằng `swagger-parser` 2.1.22 đang có trong dự án, bộ đọc YAML chuyển `no` không có dấu nháy trong mảng `required` thành boolean `false`. Đã tái hiện trên `ServedItemView`: mảng được đọc thành `[false, "type", "skill", "prompt", "rules"]`, trong khi tên property vẫn là `no`. Parser phát cảnh báo tương tự cho `ItemState`, `AnswerRequest`, `AnswerResult`.

T-02 giữ đúng field `no` theo spec và contract; mapper/client DTO không đổi tên field và các test JSON vẫn kiểm tra field này. Không sửa OpenAPI ngoài phạm vi đã duyệt. Đề nghị người giữ contract xem xét thêm dấu nháy cho `'no'` trong các mảng `required` để tương thích với bộ đọc hiện có; đây là ghi chú về công cụ, không phải một thay đổi nghiệp vụ đã tự quyết.
