# Câu hỏi và quyết định — Spec #017

## 2026-11-13 · Brainstorm với leader

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | Tính bảng bằng SQL trên `attempts` + cache, hay Redis ZSET + bảng `ranking_entries` như UC | SQL trên `attempts` + cache Redis 60 s, DB là nguồn gốc | ✅ Chốt |
| Q2 | Điểm game mỗi người trong kỳ | Ván cao nhất | ✅ Chốt |
| Q3 | Người bị khoá | Ẩn, không chiếm hạng | ✅ Chốt |
| Q4 | Số dòng | API top 20; FE hiển thị chính top 5, "Xem thêm" mở tới 20; hạng không trùng (`ROW_NUMBER`) | ✅ Chốt |
| Q5 | Mã + tên 6 game trong `learning.api.GameCode` | Theo phụ lục 008 | ⏳ Chờ 008 |

## 2026-11-13 · Duyệt G0 T-01

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T01-01 | Phạm vi sửa API module 001 | Duyệt thêm đúng `AuthApi.bannedUserIds()`, `AuthApi.publicProfiles(Set<Long>)` và `PublicProfile(displayName, avatarUrl)`; không đổi `displayNames`, `readPlan`, `lockPlan` | ✅ Duyệt |
| Q-T01-02 | Cache danh sách tài khoản bị cấm | spec dòng 107/177 'bannedUserIds() cache 60 giây' được thực hiện bằng cache kết quả bảng ở T-05; AuthApi không cache. Chấp nhận trễ tối đa 60 giây sau khi cấm/gỡ cấm | ✅ Duyệt |
| Q-T01-03 | Trạng thái bị loại khỏi bảng | Chỉ loại `banned_at IS NOT NULL`; tài khoản bị đình chỉ (`suspended`) vẫn hiện trên bảng | ✅ Duyệt |
| Q-T01-04 | Lọc tài khoản cấm trong lookup hồ sơ | `publicProfiles` chỉ lookup theo ID, không tự lọc người bị cấm; T-04 lọc trong SQL trước khi đánh hạng | ✅ Duyệt |
| Q-T01-05 | Test PostgreSQL cho AuthApi | Cho phép thêm `AuthApiIntegrationTest.java` vào allowlist T-01 | ✅ Duyệt |
| Q-T01-06 | PII, lỗi DB và input | Truy vấn hồ sơ chỉ chọn `id`, `display_name`, `avatar_url`; lỗi DB của `bannedUserIds` phải ném lên; `userIds == null` ném `IllegalArgumentException`, set rỗng trả map rỗng không truy vấn | ✅ Duyệt |

**Ghi chú cho T-04:** lọc ID bị cấm trước khi đánh hạng bằng `user_id <> ALL(:banned)` hoặc `NOT (user_id = ANY(:banned))` với mảng. Không dùng `NOT IN (...)` vì danh sách rỗng có thể tạo SQL không hợp lệ.

## 2026-11-14 · Duyệt G0 T-03 và đổi số migration

| # | Nội dung | Quyết định leader | Trạng thái |
|---|---|---|---|
| Q-T03-01 | Migration và exact allowlist | Duyệt 5 file: `V8__leaderboard_index.sql`, `LeaderboardMigrationIntegrationTest.java`, `questions.md`, `tasks.md`, `docs/reference/database.md`; SQL đúng §0.1, test migration thu gọn | ✅ ANSWERED |
| Q-T03-02 | Phân bổ version và thứ tự triển khai | Thay thế quyết định V9 trước đây: **V8 dành index `ix_att_ref_rank` của 017/T-03; V9 dành `app_settings` của 023 (chưa làm)**. 023 chưa viết, nên T-03 không phải chờ V8 khác; PR được review/merge bình thường, không HOLD. Không tạo migration V9 cho 017, không triển khai 023 | ✅ ANSWERED |
| Q-T03-03 | Phạm vi AT-16 | T-03 kiểm Flyway, catalog index và nâng cấp bảo toàn schema/dữ liệu; EXPLAIN truy vấn leaderboard để T-04 nghiệm thu | ✅ ANSWERED |

## Dữ kiện đã kiểm trên main `2735ac8`
- `attempts` có `kind` (gồm QUIZ, GAME, CONTEST), `ref_type`, `ref_id`, `game_code`, `score` BIGINT, `duration_ms`, `suspicious`, `submitted_at`.
- Index `ix_att_rank(kind, game_code, score DESC) WHERE status='SUBMITTED'`; `uq_quiz_first(user_id, ref_id) WHERE kind='QUIZ' AND status='SUBMITTED'`.
- Không có bảng `ranking_entries` trong migration.
- `auth.users.avatar_url`, `banned_at` có; `AuthApi` hiện có `displayNames`, chưa có lọc banned hàng loạt.
- ArchUnit: `learning` chỉ gọi `auth` qua `auth.api`; `auth` không biết `learning`.
