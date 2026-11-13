# Câu hỏi và quyết định — Spec #017

## 2026-11-13 · Brainstorm với leader

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | Tính bảng bằng SQL trên `attempts` + cache, hay Redis ZSET + bảng `ranking_entries` như UC | SQL trên `attempts` + cache Redis 60 s, DB là nguồn gốc | ✅ Chốt |
| Q2 | Điểm game mỗi người trong kỳ | Ván cao nhất | ✅ Chốt |
| Q3 | Người bị khoá | Ẩn, không chiếm hạng | ✅ Chốt |
| Q4 | Số dòng | API top 20; FE hiển thị chính top 5, "Xem thêm" mở tới 20; hạng không trùng (`ROW_NUMBER`) | ✅ Chốt |
| Q5 | Mã + tên 6 game trong `learning.api.GameCode` | Theo phụ lục 008 | ⏳ Chờ 008 |

## Dữ kiện đã kiểm trên main `2735ac8`
- `attempts` có `kind` (gồm QUIZ, GAME, CONTEST), `ref_type`, `ref_id`, `game_code`, `score` BIGINT, `duration_ms`, `suspicious`, `submitted_at`.
- Index `ix_att_rank(kind, game_code, score DESC) WHERE status='SUBMITTED'`; `uq_quiz_first(user_id, ref_id) WHERE kind='QUIZ' AND status='SUBMITTED'`.
- Không có bảng `ranking_entries` trong migration.
- `auth.users.avatar_url`, `banned_at` có; `AuthApi` hiện có `displayNames`, chưa có lọc banned hàng loạt.
- ArchUnit: `learning` chỉ gọi `auth` qua `auth.api`; `auth` không biết `learning`.
