# Feature Specification: Bảng xếp hạng công khai theo chủ đề và theo game

**Feature:** `017-leaderboard`
**Feature tree:** 5.3 Bảng xếp hạng công khai · **Module:** `learning` (package `learning.leaderboard`)
**Use case:** UC-082, UC-083, UC-084, UC-081 bước 5 (hạng trong kết quả quiz) · **Actor:** `GUEST`, `LEARNER`
**Màn hình:** SCR-046 Topic Leaderboard, SCR-047 Game Leaderboard
**Scope:** đợt hoàn thiện · **Priority:** P2

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — được code theo `tasks.md` |
| **Approved by** | Leader (rà và duyệt 2026-11-13; quyết định chung H1–H14 plan `261113-1629`) |
| **Locked on** | 2026-11-13 |
| **Phụ thuộc dữ liệu** | 019 ghi `attempts` QUIZ (`ref_type='QUIZ'` = tên `PracticeSource`, `ref_id` = topicId) · 007/008 ghi `attempts` GAME (`game_code`) — **không phụ thuộc code**, test bằng dữ liệu mẫu |
| **Quyết định** | Brainstorm 2026-11-13: SQL trên `attempts` + cache 60 giây · điểm game = ván cao nhất · ẩn người bị khoá |
| **Plan** | `plans/261113-1629-viet-spec-con-lai-hoan-thien/phase-07-spec-017-bang-xep-hang.md` |

> **Codex đọc:** `AGENTS.md` → file này → `contracts/openapi-leaderboard.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi (để ở đâu) |
|---|---|
| Bảng theo **chủ đề**: điểm lượt quiz **đầu tiên** của mỗi người (UC-082, BR-081-5) | Ghi lượt quiz — 019 |
| Bảng theo **game**: ván điểm cao nhất mỗi người, 6 game (H11) | Ghi kết quả game, chống gian lận lúc chơi — 007/008 |
| Kỳ **tuần / tháng / toàn thời gian** theo giờ Việt Nam (UC-084) | Xem lại kỳ đã qua (BR-084-5) |
| Top 20 (hiển thị chính top 5, bấm "Xem thêm" mở tới 20) + **hạng của tôi** khi đăng nhập; khách chỉ xem top (BR-082-3) | Bảng `ranking_entries`, Redis ZSET mô tả trong UC — **không làm** (Q1) |
| Hàm nội bộ `LeaderboardService.rankOf` cho khối hạng ở SCR-045 (019) | Bảng xếp hạng cuộc thi — 021 tự xếp |
| Ẩn người bị khoá và lượt `suspicious` (BR-082-7, BR-083-4) | Huy hiệu, phần thưởng, thông báo lên hạng |

### 0.1 · Lưu trữ — chỉ đọc bảng có sẵn

| Bảng | Dùng gì |
|---|---|
| `learning.attempts` | `user_id, kind, ref_type, ref_id, game_code, status, score, duration_ms, suspicious, submitted_at`; index `ix_att_rank(kind, game_code, score DESC) WHERE status='SUBMITTED'`, `uq_quiz_first(user_id, ref_id) WHERE kind='QUIZ' AND status='SUBMITTED'` |
| `learning.topics` | `id, name, hsk_level` cho bộ chọn chủ đề |
| `auth.users` | **Không đọc trực tiếp** — qua `AuthApi` (§0.3) |

**Một migration index** (số V xin leader, H4): `CREATE INDEX ix_att_ref_rank ON learning.attempts(kind, ref_id, submitted_at) WHERE status = 'SUBMITTED';` — lọc bảng chủ đề theo kỳ. Không thêm bảng.

### 0.2 · Hợp đồng dữ liệu với 019 và 007 (H6)

| Bảng | Điều kiện một dòng được tính |
|---|---|
| Chủ đề | `kind='QUIZ'`, `ref_type='QUIZ'`, `ref_id=topicId`, `status='SUBMITTED'`, `suspicious=false`, `score IS NOT NULL` |
| Game | `kind='GAME'`, `game_code=:code`, `status='SUBMITTED'`, `suspicious=false`, `score IS NOT NULL` |

`uq_quiz_first` chỉ cho **một** lượt QUIZ `SUBMITTED` mỗi người mỗi chủ đề → lượt làm lại quiz của 019 ghi source `QUIZ_RETRY` (`kind='PRACTICE'`) — không vào bảng — ghi vào 019 §10.

### 0.3 · File dùng chung bị sửa (báo leader trước khi mở PR — H8)

- `auth.api.AuthApi` (**module 001 — báo leader trước**): thêm `Set<Long> bannedUserIds()` và `Map<Long, PublicProfile> publicProfiles(Set<Long> userIds)`; record mới `auth.api.PublicProfile(String displayName, String avatarUrl)`.
- `learning.api.GameCode` (**mới, dùng chung với 007**): enum 6 mã game (4 mã 007/008 + 2 mã phụ lục 008). Nếu 007 tạo trước thì 017 dùng lại; ai merge trước tạo enum, người sau chỉ tham chiếu.
- `ErrorCode.java`, `SecurityConfig.java` (permitAll `/api/public/leaderboards/**`), `routeAccess.ts`, `screenRegistry.ts`, `navigation.ts`.

---

## 1 · Context & Goal

Bảng xếp hạng tạo động lực học đều và chơi lại game. Yêu cầu cốt lõi là **công bằng** (chỉ điểm server xác nhận, loại gian lận, bằng điểm thì nhanh hơn thắng) và **riêng tư** (chỉ tên hiển thị, ảnh). Dữ liệu gốc ở DB; cache chỉ để nhanh (BR-081-7, BR-082-6).

**Thành công khi:** khách xem được top tuần của một chủ đề/game; người học thấy hạng thật của mình kể cả ngoài top 20; sang thứ Hai bảng tuần về rỗng; Redis tắt vẫn chạy.

---

## 2 · User Stories

### US-1 · Xem bảng chủ đề (P1)
Là khách hoặc người học, tôi chọn một chủ đề và kỳ, thấy top 5 điểm quiz cao nhất kèm thời gian làm, mở rộng xem tới 20 người.

### US-2 · Xem bảng game (P1)
Là khách hoặc người học, tôi chọn một trong 6 game và kỳ, thấy top 5 ván cao nhất, mở rộng xem tới 20 người.

### US-3 · Biết hạng của mình (P1)
Là người học đã đăng nhập, tôi thấy hạng và điểm của mình được tô nổi, kể cả khi tôi ở ngoài top 20.

### Edge cases
- Kỳ chưa có ai → danh sách rỗng + "Chưa có ai trong kỳ này" (BR-084-6).
- Đồng điểm và đồng thời gian → ai nộp trước đứng trên; số hạng không trùng.
- Người học chưa có lượt nào trong kỳ → `myEntry = null`.
- Người bị khoá → không xuất hiện, không chiếm hạng; nếu chính người đó gọi (đã bị chặn đăng nhập ở 001) thì không áp dụng.
- Chủ đề tồn tại nhưng không có quiz → rỗng; chủ đề không tồn tại → `TOPIC_NOT_FOUND`.
- Lượt nộp đúng 00:00 thứ Hai giờ VN → thuộc tuần mới.
- Game code lạ hoặc sai chữ hoa/thường → `INVALID_GAME_CODE`.

---

## 3 · Functional Requirements

### Nhóm 0 — Quyền (FR-001…FR-003)
- **FR-001** Mọi endpoint dưới `/api/public/leaderboards/**` không cần đăng nhập (`GUEST`).
- **FR-002** Có phiên đăng nhập hợp lệ (cookie hoặc bearer) → tính thêm `myEntry` cho người đó; phiên hết hạn/sai → coi như khách, **không** trả 401.
- **FR-003** Response chỉ có `userId`, `displayName`, `avatarUrl`, `score`, `durationMs`, `rank` — không email, không câu trả lời (BR-081-6, BR-082-5).

### Nhóm 1 — Kỳ (FR-004…FR-006)
- **FR-004** `period ∈ {week, month, all}`, mặc định `week`; giá trị khác → `VALIDATION_FAILED` (gồm dạng `2026-W39`, BR-084-5).
- **FR-005** Tính theo `Asia/Ho_Chi_Minh` từ `Clock` tiêm vào: `week` = [thứ Hai 00:00 tuần hiện tại, thứ Hai kế); `month` = [ngày 1 00:00, ngày 1 tháng sau); `all` = không giới hạn. Lọc `submitted_at` trong `[start, end)`.
- **FR-006** Response trả `periodStart`, `periodEnd` (ISO-8601 có offset +07:00; `null` khi `all`) để FE hiện "Tuần 10/11 – 16/11".

### Nhóm 2 — Xếp hạng (FR-007…FR-011)
- **FR-007** Bảng chủ đề: tập dòng theo §0.2 (mỗi người tối đa 1 dòng do `uq_quiz_first`).
- **FR-008** Bảng game: mỗi người lấy **một ván tốt nhất** trong kỳ theo thứ tự FR-009 (`DISTINCT ON (user_id)`).
- **FR-009** Thứ tự: `score DESC`, `duration_ms ASC NULLS LAST`, `submitted_at ASC`, `user_id ASC`; `rank` = `ROW_NUMBER()` theo thứ tự đó (không trùng hạng).
- **FR-010** Loại `user_id = ANY(:banned)` **trong SQL trước khi đánh số hạng** (`AuthApi.bannedUserIds()`, cache 60 giây) → hạng người khác không nhảy.
- **FR-011** API trả **top 20** (`cnhsk.leaderboard.top-size = 20`, leader chốt 13/11). FE hiển thị chính **top 5**; nút "Xem thêm" mở rộng tại chỗ tới 20 (không gọi lại API), "Thu gọn" về 5.

### Nhóm 3 — Hạng của tôi (FR-012…FR-013)
- **FR-012** `myEntry` = dòng của tôi với hạng thật (tính bằng truy vấn đếm số dòng xếp trên tôi + 1, cùng điều kiện và loại người bị khoá); có cả khi tôi ở ngoài top. Không có dòng → `null`. Khách → field `myEntry` = `null`.
- **FR-013** `LeaderboardService.rankOf(userId, topicId)` (nội bộ module learning) trả `{rank, total}` hoặc rỗng — 019 dùng cho SCR-045 (kỳ `all`). 019 chưa merge thì không ai gọi; 017 không chờ.

### Nhóm 4 — Bộ chọn (FR-014…FR-015)
- **FR-014** `GET /api/public/leaderboards/topics?hsk=` trả danh sách chủ đề `{id, name, hskLevel}` sắp `hsk_level, order_index` (API chủ đề có sẵn chỉ cho `LEARNER`).
- **FR-015** `GET /api/public/leaderboards/games` trả 6 game `{code, name}` từ `learning.api.GameCode` (tên tiếng Việt trong enum).

### Nhóm 5 — Cache (FR-016…FR-017)
- **FR-016** Top N (không gồm `myEntry`) cache Redis 60 giây, khoá `lb:{topic|game}:{ref}:{period}:{periodStartEpochDay}` → sang kỳ mới tự dùng khoá mới, khoá cũ tự hết hạn.
- **FR-017** Redis lỗi/timeout (≤ 200 ms) → đọc thẳng DB, ghi log WARN, **không** trả lỗi (BR-082-6, T2).

### Nhóm 6 — Giao diện (FR-018…FR-022)
- **FR-018** Route công khai `/leaderboards/topics` (SCR-046, query `?topicId=&period=`) và `/leaderboards/games` (SCR-047, `?game=&period=`), layout công khai; mục "Bảng xếp hạng" ở menu công khai và menu học viên.
- **FR-019** Bộ chọn chủ đề (lọc HSK) / game; tab kỳ Tuần · Tháng · Toàn thời gian; dòng mô tả khoảng ngày giờ VN.
- **FR-020** Bảng (mặc định 5 dòng, "Xem thêm" mở tới 20): hạng (1–3 có biểu tượng kèm chữ, không chỉ màu), ảnh, tên (link SCR-042 chỉ khi người xem là `LEARNER` và màn 042 đã có), điểm, thời gian `mm:ss`. Dòng của tôi tô nổi trong top; ngoài top → hiện riêng dưới bảng "Hạng của bạn: #128".
- **FR-021** Đủ 4 trạng thái; khách thấy lời mời "Đăng nhập để xem hạng của bạn". Responsive: ≤ 640 px bảng thành danh sách thẻ, không cuộn ngang.
- **FR-022** Dùng `shared/ui` + token `design.md` (H10). Web game (`cnhsk-game`) link sang `/leaderboards/games?game={code}` của web chính (007 làm nút).

### Key Entities
`LeaderboardEntry(rank, userId, displayName, avatarUrl, score, durationMs)` · `Leaderboard(type, ref, period, periodStart, periodEnd, entries, myEntry)` — không lưu, tính khi đọc.

---

## 4 · Data Model

Không bảng mới. Một index (§0.1). SQL tham chiếu (bảng game, tuần):

```sql
WITH best AS (
  SELECT DISTINCT ON (user_id) user_id, score, duration_ms, submitted_at
  FROM learning.attempts
  WHERE kind = 'GAME' AND game_code = :code AND status = 'SUBMITTED'
    AND suspicious = false AND score IS NOT NULL
    AND submitted_at >= :start AND submitted_at < :end
    AND user_id <> ALL(:banned)
  ORDER BY user_id, score DESC, duration_ms ASC NULLS LAST, submitted_at ASC
)
SELECT ROW_NUMBER() OVER (ORDER BY score DESC, duration_ms ASC NULLS LAST,
                                   submitted_at ASC, user_id ASC) AS rank, *
FROM best ORDER BY rank LIMIT :n;
```

Bảng chủ đề giống nhưng không cần `DISTINCT ON` (đã duy nhất do `uq_quiz_first`), điều kiện `kind='QUIZ' AND ref_type='QUIZ' AND ref_id=:topicId`.

---

## 5 · Error Matrix

| Mã | HTTP | Khi nào |
|---|---|---|
| `TOPIC_NOT_FOUND` | 404 | Có sẵn — chủ đề không tồn tại |
| `INVALID_GAME_CODE` *(mới)* | 422 | `gameCode` không thuộc `GameCode` |
| `VALIDATION_FAILED` | 422 | `period` sai, `hsk` ngoài 1–9 |
| `RATE_LIMITED` | 429 | > 120 request/phút/IP (khách) — dùng `RateLimiter` có sẵn |

Mã mới thêm ngay trước `INTERNAL_ERROR` theo thứ tự merge (H8). Redis lỗi **không** là lỗi API (FR-017).

---

## 6 · Architectural Constraints
- Module `learning`; gọi `auth` chỉ qua `AuthApi` (ArchUnit `learning_chi_duoc_goi_auth_qua_api`); không gọi `community`.
- Không ghi DB. Không lắng nghe sự kiện nộp bài (tính khi đọc).
- `GameCode` ở `learning.api` để `community` (007) dùng được mà không vi phạm ranh giới (H14).

## 7 · Non-Functional Requirements
- p95 < 300 ms (không cache) với 10.000 lượt/bảng; < 50 ms khi trúng cache. AT kiểm `EXPLAIN` dùng `ix_att_rank` / `ix_att_ref_rank`.
- `bannedUserIds()` cache 60 giây; số người bị khoá giả định < 1.000.
- Không log `displayName`; log chỉ loại bảng, mã, kỳ, thời gian.

## 8 · Constitution Check
| Luật | Đáp ứng |
|---|---|
| AC-04 số bảng | Không thêm bảng |
| AC-03 ranh giới module | `AuthApi`, `GameCode` ở `learning.api` |
| AC-07 điểm số nguyên | `score` BIGINT |
| Giờ Việt Nam | FR-005 dùng `Asia/Ho_Chi_Minh` + `Clock` |
| PII | FR-003 |
| LESSON-003 | Chỉ điểm server đã ghi; loại `suspicious` |

## 9 · Quyết định và câu cần leader xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Tính bằng SQL trên `attempts` + cache 60 giây, không `ranking_entries`/ZSET | ✅ Leader chốt 2026-11-13 |
| Q2 | Điểm game = ván cao nhất trong kỳ | ✅ Leader chốt 2026-11-13 |
| Q3 | Ẩn người bị khoá, không chiếm hạng | ✅ Leader chốt 2026-11-13 |
| Q4 | API top 20, hiển thị chính top 5, mở rộng tới 20; hạng không trùng (ROW_NUMBER) | ✅ Leader chốt 2026-11-13 |
| Q5 | Tên tiếng Việt 6 game trong `GameCode` (lấy theo app `cnhsk-game`: Mưa Chữ, Ghép Pinyin, Ghép Bộ Thủ, Bắt Chữ, Thần Bút, Phi Đao) | ⏳ Chờ phụ lục 008 chốt mã |

## 10 · Móc nối
- **019:** ghi lượt quiz đầu tiên `kind='QUIZ'`, `ref_type='QUIZ'`, `SUBMITTED`; lượt làm lại ghi kiểu khác (do `uq_quiz_first`); SCR-045 gọi `LeaderboardService.rankOf` và link `/leaderboards/topics?topicId=`.
- **007/008:** ghi `attempts` GAME với `game_code` ∈ `GameCode`, `score` server tính, `suspicious` theo luật chống gian lận; nút "Bảng xếp hạng" ở web game.
- **001:** `AuthApi` thêm 2 hàm (§0.3).
- **Phụ lục 009 (SCR-042):** tên trên bảng link sang hồ sơ khi màn đã có.
- **015:** thêm đoạn `help/` về bảng xếp hạng sau khi merge.

## 11 · Rủi ro đã chấp nhận
- Dữ liệu thật chỉ có khi 019/007 merge → demo trước bằng dữ liệu mẫu, sau ⚿K3 mới có bảng game thật.
- Cache 60 giây → người vừa nộp có thể chưa thấy mình trong top ngay; `myEntry` luôn tính trực tiếp nên vẫn thấy hạng.
- `bannedUserIds()` trả toàn bộ — chấp nhận ở quy mô đồ án.

## 12 · Màn hình
- **SCR-046:** tiêu đề; bộ chọn HSK → chủ đề; tab kỳ; dòng khoảng ngày; bảng top 5 + "Xem thêm" (tới 20); khối "Hạng của bạn" / lời mời đăng nhập.
- **SCR-047:** như trên với bộ chọn 6 game.

## 13 · API Contract
`contracts/openapi-leaderboard.yaml` — `GET /api/public/leaderboards/topics`, `GET /api/public/leaderboards/topics/{topicId}`, `GET /api/public/leaderboards/games`, `GET /api/public/leaderboards/games/{gameCode}`.

## 14 · Acceptance Tests

| AT | Kịch bản | Kỳ vọng |
|---|---|---|
| AT-01 | Khách xem top tuần chủ đề có 60 người | API 20 dòng, sắp đúng, `myEntry=null`, không email; FE hiện 5, "Xem thêm" hiện đủ 20 |
| AT-02 | Hai người cùng điểm, A làm 120 s, B 90 s | B hạng trên A |
| AT-03 | Cùng điểm, cùng thời gian | Người nộp trước hạng trên; hạng không trùng |
| AT-04 | Lượt `suspicious=true` điểm cao nhất | Không có trên bảng |
| AT-05 | Người bị khoá đứng đầu theo điểm | Không xuất hiện; người kế là hạng 1 |
| AT-06 | Game: một người 3 ván 50/80/70 | Một dòng điểm 80 |
| AT-07 | Nộp lúc 23:59 Chủ nhật và 00:00 thứ Hai giờ VN, xem `week` vào thứ Hai | Chỉ lượt 00:00 thứ Hai được tính |
| AT-08 | Xem `month` ngày 1 | Chỉ lượt từ ngày 1 00:00 giờ VN |
| AT-09 | Người học hạng 128 xem top | `myEntry.rank=128`, có điểm |
| AT-10 | Người học chưa chơi game trong kỳ | `myEntry=null` |
| AT-11 | `period=2026-W39` | 422 `VALIDATION_FAILED` |
| AT-12 | `gameCode=char_rain` (sai hoa/thường) | 422 `INVALID_GAME_CODE` |
| AT-13 | Redis tắt | Vẫn 200, kết quả đúng |
| AT-14 | Gọi hai lần trong 60 s, có lượt mới ở giữa | Lần 2 trả cache; `myEntry` phản ánh lượt mới |
| AT-15 | Token hết hạn gọi endpoint công khai | 200 như khách, không 401 |
| AT-16 | `EXPLAIN` truy vấn chủ đề và game | Dùng `ix_att_ref_rank` / `ix_att_rank` |
| AT-17 | FE ≤ 640 px | Danh sách thẻ, không cuộn ngang; top 3 có chữ kèm biểu tượng |
| AT-18 | Hợp đồng `/v3/api-docs` so `openapi-leaderboard.yaml` | Khớp path/method/status hai chiều |

## 15 · Gói việc
Xem `tasks.md` — 7 task, ước tính BE 2,5 · FE 1,5 ngày công.
