---
phase: 7
title: "Spec 017 bảng xếp hạng"
status: pending
priority: P2
dependencies: [1]
effort: "spec 1,5 · BE 2,5 · FE 1,5 ngày công"
owner: "Dũng (cùng người làm 021 — cùng logic xếp hạng điểm/thời gian)"
---

# Phase 7: Spec 017 — Bảng xếp hạng công khai

## Overview
Feature 5.3. Màn SCR-046 Topic Leaderboard, SCR-047 Game Leaderboard (cả hai trong `cnhsk-web`; web game chỉ link sang). **Không bảng mới.** Thiết kế để **không phụ thuộc code** của 019 và 007 (H6).

## Context links
- UC-082 BXH theo chủ đề, UC-083 BXH theo game, UC-084 Lọc tuần/tháng/all-time, UC-081 (use-cases-06)
- `attempts` index `ix_att_rank(kind, game_code, score DESC) WHERE status='SUBMITTED'`, `uq_quiz_first`
- Phase 2 (007 §10) và phase 3 (019) — hợp đồng ghi `attempts`
- Redis (đã có), `app.timezone`

## Thiết kế đề xuất
- Module `learning.leaderboard`, đọc **thẳng** `attempts` (cùng module learning, đúng ArchUnit).
- **Chủ đề:** kind `QUIZ`, `ref_type='QUIZ'`, chỉ lượt đầu (`uq_quiz_first`); xếp điểm DESC, `duration_ms` ASC, `submitted_at` ASC.
- **Game:** kind `GAME`, theo `game_code`; điểm cao nhất mỗi người trong kỳ.
- **Kỳ (UC-084):** tuần/tháng tính theo giờ VN (thứ Hai 00:00), all-time. API top 20, hiển thị chính top 5, "Xem thêm" mở tới 20 (leader 13/11) + "hạng của tôi".
- Cache Redis 60 giây theo khoá (loại, mã, kỳ); không cần job.
- Riêng tư: chỉ tên hiển thị + ảnh, link hồ sơ 042 (phụ lục 009); loại tài khoản bị khoá; loại lượt `suspicious`.
- API đọc nội bộ `LeaderboardApi.rankOf(userId, kind, ref)` cho 019 (khối hạng ở 045) — **019 không chờ**: nếu chưa merge thì 019 ẩn khối.

## Requirements
- Functional: 2 màn, bộ lọc kỳ, chọn chủ đề/game, hạng của tôi, trạng thái rỗng.
- Non-functional: truy vấn dùng index sẵn có (kiểm `EXPLAIN` trong AT), p95 < 300 ms với 10k lượt.

## Implementation Steps (viết spec)
1. FR + edge: đồng điểm, chưa có ai chơi, người bị khoá, chủ đề không tồn tại.
2. API `contracts/openapi-leaderboard.yaml`: `GET /api/public/leaderboards/topics/{topicId}?period=`, `GET /api/public/leaderboards/games/{gameCode}?period=`, `GET /api/leaderboards/me?...` (cần đăng nhập).
3. AT dùng dữ liệu fixture `attempts` (không cần 019/007 merge).

## Success Criteria
- [ ] Không gọi code 019/007; test chạy được với fixture
- [ ] Đúng hợp đồng H6

## Risk Assessment
- Nếu index thiếu cho lọc theo kỳ → thêm index bằng migration (xin số V ở leader, H4).
- Dữ liệu game thật chỉ có khi ⚿K3 (007) merge → demo game LB sau đó.
