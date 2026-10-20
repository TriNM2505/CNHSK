# Feature Specification: Luyện phát âm 8 tầng

**Feature:** `005-pronunciation-stages`
**Feature tree:** 1.3 Luyện phát âm · **Module:** `learning` (package `learning.pronunciation`)
**Use case:** UC-021 (luyện tầng), UC-022 («include» mở tầng kế) · **Actor:** `LEARNER`
**Màn hình:** SCR-016 Pronunciation Practice
**Scope:** MVP · **Priority:** P1

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | DRAFT — chờ cả nhóm duyệt |
| **Phụ thuộc** | Spec 001 · Spec 002 (`PRON_STAGE`, `PRON_UNIT`, cột `pron_stages.units`) · Spec 003 §0 |
| **Quyết định chung** | Brainstorm 2026-10-19: D-02, D-03, D-04, D-07 |

> **Codex đọc:** `AGENTS.md` → spec 003 §0 → file này → `contracts/openapi-pronunciation.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| 8 tầng nghe-và-chọn (thanh mẫu, vận mẫu, thanh điệu, biến điệu…) | Chấm phát âm qua micro (BR-021-7) |
| Tầng 1 mở sẵn; đạt ≥ `pass_percent` (mặc định 80%, `BUS-11`) tự mở tầng kế | Gắn tầng với nút `NA` của cây tri thức |
| Làm lại, giữ điểm cao nhất, **không bao giờ khoá lại** tầng đã mở | Âm thanh tự sinh (TTS) — dữ liệu mang `audio_url` |
| Tiếp tục tầng đang làm dở (lưu từng đơn vị) | |

Đây là luật cục bộ của tính năng 1.3 nên **giữ mở tầng** dù cây tri thức hoãn (D-07). Ghi tiến độ vào `learning.user_progress`
(`target_type='PRON_STAGE'`, `target_id = pron_stages.id`) — bảng đã có khoá chính `(user_id, target_type, target_id)` chặn đua (UC-022).

**Nguồn lượt mới:** `PRON_STAGE` — `kind=PRACTICE`, `ref_id = pron_stages.id`, `IMMEDIATE`, hạn **7 ngày**, `allowSkip = false`.
**Loại câu mới:** `PRON_CHOICE` (`gradable`, `skill=PRONUNCIATION`).

**Không thay đổi DB** (cột `units` do spec 002 V3 thêm).

---

## 1 · Context & Goal

| Mã | Business goal | Đóng góp |
|---|---|---|
| **BG-01** | Phù hợp người Việt | `target_sounds` tập trung cặp âm dễ lẫn (`zh/z`, `ch/c`, `sh/s` — `DK-06`) |

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Đạt ≥ 80% tầng N → tầng N+1 mở trong cùng response | 100% |
| **SC-002** | Làm lại điểm thấp hơn → tầng đã mở vẫn mở, điểm cao nhất giữ nguyên | 100% |
| **SC-003** | Hai tab cùng nộp tầng cuối → đúng 1 dòng tiến độ cho tầng sau | 100% |

---

## 2 · User Stories

### US-1 · Luyện tầng 1 (P1)
**Given** tôi mới vào "Luyện phát âm"
**Then** tầng 1 mở, tầng 2–8 khoá kèm "Hoàn thành tầng trước với ≥ 80%"
**When** tôi làm hết 10 bài nghe của tầng 1, đúng 9
**Then** tôi thấy "90% — đã mở tầng 2"

### US-2 · Làm dở rồi quay lại (P1)
**Given** tôi làm 4/10 bài rồi thoát
**When** hôm sau tôi mở lại tầng 1
**Then** tôi làm tiếp từ bài thứ 5, 4 bài cũ giữ kết quả

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Gọi tầng 5 qua URL khi mới xong tầng 1 | **403** `STAGE_LOCKED` (BR-021-8) |
| E2 | Làm lại tầng 1 được 70% (cũ 85%) | Giữ 85%, tầng 2 vẫn mở (A1, BR-021-4/5) |
| E3 | 1/10 bài không tải được audio, đúng 9/9 còn lại | Độ chính xác 100% (BR-021-6) |
| E4 | Tầng chưa có đơn vị | Hiện "Đang cập nhật", không cho bắt đầu (`STAGE_HAS_NO_UNITS`) |
| E5 | Đạt tầng 8 (tầng cuối) | `completedAllStages = true`, không tạo tầng 9 |
| E6 | Tầng N+1 chưa được nhập lúc đạt tầng N | Không lỗi; khi tầng N+1 được nhập sau, nó **mở** vì tầng N đã đạt (FR-004) |
| E7 | Nộp khi còn bài chưa làm | **422** `STAGE_INCOMPLETE` kèm `remaining`, lượt vẫn mở |
| E8 | Nhập lại tầng N tăng `pass_percent` lên 90% | Tầng N+1 đã mở **vẫn mở** (có dòng tiến độ, FR-004) |

---

## 3 · Functional Requirements

### Nhóm 1 — Danh sách tầng (FR-001…FR-005)

| Mã | Yêu cầu |
|---|---|
| **FR-001** | `GET /api/pronunciation/stages` SHALL trả mọi dòng `pron_stages` sắp `stage_index`: `{id, stageIndex, name, description, targetSounds, passPercent, unitCount, playable, state, bestAccuracy, activeSessionId, activeAnswered}`; `id` = `pron_stages.id`, frontend dùng làm `refId` khi khôi phục lượt qua `/api/practice/sessions/active` |
| **FR-002** | `playable = unitCount > 0` (đơn vị có `audioUrl`); `unitCount = 0` → giao diện hiện "Đang cập nhật" (E4) |
| **FR-003** | `state ∈ {LOCKED, AVAILABLE, COMPLETED}`: `COMPLETED` nếu `bestAccuracy ≥ passPercent` của chính tầng đó; ngược lại `AVAILABLE` nếu **mở** (FR-004); còn lại `LOCKED` |
| **FR-004** | Tầng `k` **mở** ⇔ `k = 1` **hoặc** có dòng `user_progress(PRON_STAGE, id tầng k)` **hoặc** tầng `k−1` đang `COMPLETED`. (Mệnh đề cuối xử lý E6; mệnh đề giữa xử lý E8) |
| **FR-005** | `bestAccuracy` = `user_progress.percent` (NUMERIC 5,2) hoặc `null`; `activeSessionId`/`activeAnswered` = lượt `PRON_STAGE` đang làm của tầng (nếu có) và số bài đã trả lời |

### Nhóm 2 — Làm một tầng (FR-006…FR-011)

| Mã | Yêu cầu |
|---|---|
| **FR-006** | `POST /api/pronunciation/stages/{stageIndex}/sessions` SHALL kiểm: `stageIndex` 1–8 và có dòng → ngược lại **404** `STAGE_NOT_FOUND`; tầng mở (FR-004) → ngược lại **403** `STAGE_LOCKED` kèm `requiredStageIndex`; `unitCount > 0` → ngược lại **422** `STAGE_HAS_NO_UNITS` |
| **FR-007** | WHEN có lượt `PRON_STAGE` `IN_PROGRESS` chưa hết hạn của tầng, THE system SHALL trả lại lượt đó (`200`, US-2) |
| **FR-008** | Lượt mới (`201`) SHALL gồm **mọi** đơn vị của tầng, thứ tự ngẫu nhiên, mỗi đơn vị một câu `PRON_CHOICE`: `prompt.audioUrl`, `prompt.text = prompt`, `options` trộn và gán lại khoá (spec 003 G-6), `answer`, `explanation`, `rules = {hasAudio:true, maxReplays:3, minDurationMs:500}` |
| **FR-009** | Trả lời dùng bộ máy spec 003 (`IMMEDIATE`); `skipped` → **400** `SKIP_NOT_ALLOWED`; `audioFailed` hợp lệ (E3) |
| **FR-010** | `PronStageHandler.onFinish` SHALL ném **422** `STAGE_INCOMPLETE` (`remaining` = số câu chưa có `attempt_items`) khi còn câu chưa trả lời → rollback, lượt giữ `IN_PROGRESS` (E7) |
| **FR-011** | `accuracy = round(correct * 100 / counted, 2)` với `counted` = số câu có `is_correct` khác `NULL` (loại `audioFailed`); `counted = 0` → `accuracy = null`, không cập nhật tiến độ, `extra.noScore = true` |

### Nhóm 3 — Ghi tiến độ và mở tầng (FR-012…FR-016) · UC-022

Tất cả trong **cùng transaction** với `finish` (BR-022-3).

| Mã | Yêu cầu |
|---|---|
| **FR-012** | THE system SHALL upsert `user_progress(user, 'PRON_STAGE', stageId)`: `percent = GREATEST(cũ, accuracy)` (BR-021-5), `state = 'COMPLETED'` nếu `percent ≥ pass_percent` ngược lại `'AVAILABLE'`, `unlocked_at` giữ nguyên nếu đã có (tầng 1: `now()` lần đầu), `last_seen_at = updated_at = now()`. Dùng `INSERT … ON CONFLICT (user_id, target_type, target_id) DO UPDATE` |
| **FR-013** | WHEN `accuracy ≥ pass_percent` **và** tồn tại tầng `stage_index + 1`, THE system SHALL `INSERT` `user_progress` tầng kế (`state='AVAILABLE'`, `percent NULL`, `unlocked_at=now()`) `ON CONFLICT DO NOTHING`; `unlockedNextStage = (số dòng chèn = 1)` (BR-022-4, SC-003) |
| **FR-014** | Mỗi lần kết thúc mở **tối đa một** tầng kế (BR-022-6) |
| **FR-015** | WHEN đạt và là tầng có `stage_index` lớn nhất trong `pron_stages`, `completedAllStages = true` (E5) |
| **FR-016** | `extra` SHALL là `{stageIndex, accuracy, bestAccuracy, passPercent, passed, unlockedNextStage, nextStageIndex, needed, completedAllStages, noScore}`; `needed = max(0, passPercent − accuracy)` khi chưa đạt (UC-022 A1) |

### Nhóm 4 — Giao diện (FR-017…FR-020) · SCR-016

| Mã | Yêu cầu |
|---|---|
| **FR-017** | Trang danh sách SHALL hiện 8 thẻ tầng: số tầng, tên, mô tả, âm luyện (`targetSounds` dạng chip), trạng thái (khoá có lý do "Hoàn thành tầng {k−1} với ≥ {pass}%", đang học, hoàn thành), độ chính xác tốt nhất, nút "Bắt đầu"/"Tiếp tục ({activeAnswered}/{unitCount})"/"Luyện lại" |
| **FR-018** | Màn làm bài SHALL: nút phát audio (người dùng bấm, không tự phát), đếm nghe lại tối đa 3, nút "Không nghe được", 4 lựa chọn, phản hồi đúng/sai + giải thích, thanh tiến độ "{answered}/{total}", **không** có nút "Bỏ qua" |
| **FR-019** | Khi trả lời câu cuối, frontend SHALL tự gọi `finish` và hiện kết quả: độ chính xác, điểm tốt nhất, "Đã mở tầng {n}" (hiệu ứng nhẹ, tôn trọng `prefers-reduced-motion`) hoặc "Còn {needed}% nữa", "Hoàn thành lộ trình phát âm" khi `completedAllStages` |
| **FR-020** | Nút "Luyện lại" trên tầng `COMPLETED` SHALL tạo lượt mới (khi không có lượt đang làm) |

---

## 4 · Data Model

Đọc `learning.pron_stages` (`units` theo spec 002 §4.7). Ghi `user_progress` (FR-012, FR-013), `attempts`/`attempt_items`/`study_events` (bộ máy).

```sql
-- FR-012 (tham chiếu)
INSERT INTO learning.user_progress (user_id, target_type, target_id, percent, state, unlocked_at, last_seen_at, updated_at)
VALUES (:u, 'PRON_STAGE', :stageId, :acc, :state, now(), now(), now())
ON CONFLICT (user_id, target_type, target_id) DO UPDATE
SET percent = GREATEST(COALESCE(learning.user_progress.percent, 0), EXCLUDED.percent),
    state = CASE WHEN GREATEST(COALESCE(learning.user_progress.percent, 0), EXCLUDED.percent) >= :pass
                 THEN 'COMPLETED' ELSE 'AVAILABLE' END,
    last_seen_at = now(), updated_at = now();
```

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `STAGE_NOT_FOUND` | 404 | `stageIndex` ngoài 1–8 hoặc chưa nhập | FR-006 |
| `STAGE_LOCKED` | 403 | Tầng chưa mở, kèm `requiredStageIndex` | FR-006 |
| `STAGE_HAS_NO_UNITS` | 422 | Tầng không có đơn vị | FR-006 |
| `STAGE_INCOMPLETE` | 422 | Kết thúc khi còn bài, kèm `remaining` | FR-010 |
| `SKIP_NOT_ALLOWED` | 400 | Gửi `skipped` | FR-009 |
| Lỗi bộ máy | | Spec 003 §5 (`UNIT_NOT_IN_STAGE` của UC-021 = `ITEM_NOT_IN_SESSION`) | |

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **BUS-11** | 80% mặc định, đọc từ `pass_percent` từng tầng (BR-022-2: cấu hình tập trung — ở dữ liệu, không hardcode) |
| **BUS-02** | FR-012, FR-013 cùng transaction với `finish` |
| **BR-021-8** | Kiểm khoá ở server (FR-006) |
| Package | `learning.pronunciation` (`PronStageQueryService`, `PronStageSessionService`, `PronStageHandler`, `web.PronunciationController`) |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | `GET /stages` | < 100 ms p95 |
| `NFR-P02` | Kết thúc tầng (gồm mở tầng) | < 300 ms p95 |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Mastery/FSRS | Không — `user_progress` chỉ dùng cột `percent`, `state`, `unlocked_at` |
| Khoá lại tầng đã mở | Không thể (FR-004, FR-012 dùng `GREATEST`) |

---

## 9 · Quyết định và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Không cho bỏ qua bài trong tầng | Chốt — bỏ qua làm sai lệch độ chính xác tầng |
| Q2 | Tối đa 3 lần nghe lại mỗi bài | Chốt (thống nhất UC-020) |
| Q3 | Hạn lượt 7 ngày | Chốt — đủ cho "lần sau vào tiếp" (UC-021 A2) |
| Q4 | Tầng mở cả khi tầng trước đạt nhưng tầng sau nhập muộn (FR-004) | Chốt |

---

## 10 · Móc nối cây tri thức

| Chỗ | Bây giờ | Khi có cây |
|---|---|---|
| `ItemGradedEvent` (`PRONUNCIATION`) | Không ai nghe | Cập nhật nút `NA` (thanh mẫu/vận mẫu/thanh điệu) tương ứng `target_sounds` |

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Người học bấm "Không nghe được" mọi câu khó để nâng độ chính xác | Ghi `meta.audioFailed`; tỉ lệ cao bất thường đánh `suspicious` (spec 003 E-F5 tính trên câu đã chấm — bổ sung: `PronStageHandler` đánh `suspicious` khi `audioFailed > 30%` tổng câu) |

---

## 12 · Màn hình

| SCR | Route | Guard | API | Field |
|---|---|---|---|---|
| SCR-016 Pronunciation Practice | `/learn/pronunciation` (danh sách) · `/learn/pronunciation/stages/:stageIndex` (làm bài) | `LEARNER` | §13 | `screen-fields.md` dòng 7732 |

SCR-016 không có open issue. Trạng thái: Loading · Empty ("Nội dung phát âm đang được cập nhật" khi chưa có tầng nào) · Error.

---

## 13 · API Contract

[`contracts/openapi-pronunciation.yaml`](contracts/openapi-pronunciation.yaml)

| Method · Path | Thành công | Lỗi |
|---|---|---|
| `GET /api/pronunciation/stages` | 200 `[StageSummary]` | 401 |
| `POST /api/pronunciation/stages/{stageIndex}/sessions` | 201 mới · 200 tiếp tục (`SessionView`) | 403, 404, 422, 429 |
| Trả lời / kết thúc | Bộ máy spec 003; `finish.extra` = `PronStageExtra` | 400, 422 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001, FR-003 | Người học mới · T tầng 1 `AVAILABLE`, 2–8 `LOCKED` |
| AT-02 | FR-002, E4 | Tầng 3 không có đơn vị · T `playable=false`; bắt đầu · T 422 `STAGE_HAS_NO_UNITS` |
| AT-03 | FR-006, E1 | Bắt đầu tầng 5 khi mới xong tầng 1 · T 403, `requiredStageIndex=4` |
| AT-04 | FR-006 | `stageIndex=9` · T 404 |
| AT-05 | FR-007, US-2 | Làm 4/10, gọi bắt đầu lại · T cùng lượt, `answered` có 4 câu |
| AT-06 | FR-008 | T đủ mọi đơn vị, không lộ `answer` |
| AT-07 | FR-009 | `skipped=true` · T 400 `SKIP_NOT_ALLOWED` |
| AT-08 | FR-010, E7 | Kết thúc khi còn 6 câu · T 422 `remaining=6`, lượt vẫn `IN_PROGRESS`, không có `user_progress` |
| AT-09 | FR-011, E3 | 1 câu `audioFailed`, 9/9 đúng · T `accuracy=100.00` |
| AT-10 | FR-012, FR-013, SC-001 | 9/10 · T tầng 1 `COMPLETED` 90.00, tầng 2 có dòng `AVAILABLE`, `unlockedNextStage=true` |
| AT-11 | FR-012, E2 | Làm lại 7/10 · T `percent` vẫn 90.00, tầng 2 vẫn mở |
| AT-12 | FR-013 | Đạt lần 2 · T `unlockedNextStage=false`, vẫn 1 dòng tầng 2 |
| AT-13 | FR-013, SC-003 | 2 lượt khác nhau cùng tầng 1, kết thúc song song · T đúng 1 dòng tầng 2 |
| AT-14 | FR-014 | Đạt 100% tầng 1 · T chỉ tầng 2 mở, không phải 3 |
| AT-15 | FR-015, E5 | Đạt tầng 8 · T `completedAllStages=true`, không có dòng nào khác |
| AT-16 | FR-004, E6 | Đạt tầng 2 khi chưa nhập tầng 3; sau đó nhập tầng 3 · T tầng 3 `AVAILABLE` |
| AT-17 | FR-004, E8 | Tầng 2 đã mở; nhập lại tầng 1 với `pass_percent=95` · T tầng 2 vẫn `AVAILABLE` |
| AT-18 | FR-016 | 7/10 · T `passed=false`, `needed=10.00` |
| AT-19 | FR-016 | Lỗi ghi `user_progress` (giả lập) · T kết thúc rollback, lượt `IN_PROGRESS` (UC-022 T6) |
| AT-20 | §11 | 4/10 câu `audioFailed` · T `suspicious=true` |
| AT-21 | FR-017…FR-020 (FE) | Vitest: thẻ khoá có lý do; không có nút "Bỏ qua"; tự kết thúc ở câu cuối; hiển thị "Còn {needed}%" |

---

## 15 · Gói việc cho Codex

[`tasks.md`](tasks.md)
