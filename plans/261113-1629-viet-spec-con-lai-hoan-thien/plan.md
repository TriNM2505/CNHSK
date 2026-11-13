---
title: "Viết spec còn lại để hoàn thiện CNHSK (017–024, phụ lục 014, game 007/008)"
description: "Kế hoạch viết 7 spec mới + phụ lục 014 + duyệt/cập nhật 007/008, tối ưu để viết và code song song; phụ thuộc không tránh được xếp hàng đợi theo task"
status: pending
priority: P1
branch: ""
tags: [spec, dot-hoan-thien, parallel]
blockedBy: []
blocks: []
created: "2026-11-13T09:31:32.690Z"
createdBy: "ck:plan"
source: skill
---

# Viết spec còn lại để hoàn thiện CNHSK

## Bối cảnh (chốt với leader 2026-11-13)

- Spec 001–015 đã có; còn dở: 012/T-19, 013/T-09…T-14, 015/T-07, menu 014.
- **Bỏ mobile (6.5)** · **gác 016 Video** (SCR-037/038) · **giữ 022 giáo viên chấm bài thuê** (leader chốt 13/11) · **chưa redesign FE** — đợt này chỉ làm đủ tính năng.
- Game: nhánh `feat/dat-six-games` có app `cnhsk-game/` 6 game client-only, **thiếu 3 file dữ liệu bị `.gitignore` chặn**, không gọi API; backend 007 (APPROVED, 11 task) và 008 (DRAFT, 8 task) chưa làm.
- **Người viết spec: leader viết như bình thường** (validate 13/11); cột "Người làm" bên dưới là người **code** sau khi spec `APPROVED`. Khuôn spec = `specs/014-library/` (spec.md §0–15, tasks.md, questions.md, contracts/openapi-*.yaml).

## Phát hiện quan trọng giúp viết song song

**Schema V1/V2 đã có gần hết bảng cần dùng** → hầu hết spec mới **không thêm bảng**:

| Spec | Tái dùng (đã có trong migration) |
|---|---|
| 007/008 game | `learning.attempts` kind `GAME` + `game_code`, `PracticeSource.GAME_ROUND` |
| 017 xếp hạng | `attempts` index `ix_att_rank`, `uq_quiz_first` |
| 019 quiz + hồ sơ | `attempts` kind `QUIZ`, `community.reactions` FOLLOW, `posts` |
| 020 nhắc học | `auth.users.reminder_time/reminder_channels`, `study_events` `REMINDER_SENT` + `uq_reminder` |
| 021 cuộc thi | `community.contests`, `attempts` kind `CONTEST`, `exams` |
| 023 quản trị | `exams`, `exam_questions`, `topics.prereq_ids` |
| 024 AI sinh bài | `service_requests` `AI_GENERATE`, `questions` `source`/`PENDING_REVIEW`, `PaidFeature.AI_GENERATE`, `ClaudeClient` |
| 022 chấm thuê | `service_requests` `GRADING` (`assignee_id`, `deadline_at`), `PaidFeature.GRADING`, `TEACHER_PAYOUT`/`REFUND` |
| Phụ lục 014 | `collections.is_public/shared_at` + `ix_coll_public` |

Chỉ **2 bảng mới**: `learning.reference_items` (018) và `shared.app_settings` (023, màn 078 sửa được).

## Phases

| # | Phase | Người code sau khi duyệt | Phụ thuộc | Status |
|---|---|---|---|---|
| 1 | [Hợp đồng chung + RFC đợt hoàn thiện](./phase-01-hop-dong-chung-va-rfc-dot-hoan-thien.md) | Leader | — | Pending |
| 2 | [Game 007/008 nối cnhsk-game](./phase-02-game-007-008-noi-cnhsk-game.md) | Đạt | 1 (chỉ để APPROVED) | Pending |
| 3 | [Spec 019 quiz theo chủ đề (043–045)](./phase-03-spec-019-quiz-cong-dong-va-ho-so.md) | Tường | 1 | Pending |
| 4 | [Spec 020 nhắc lịch học qua email](./phase-04-spec-020-nhac-lich-hoc-qua-email.md) | Dũng | 1 | Pending |
| 5 | [Spec 021 cuộc thi có thưởng](./phase-05-spec-021-cuoc-thi-co-thuong.md) | Dũng | 1 | Pending |
| 6 | [Spec 023 quản trị đề thi, tiên quyết, cài đặt](./phase-06-spec-023-quan-tri-de-thi-tien-quyet-cai-dat.md) | Tường | 1 | Pending |
| 7 | [Spec 017 bảng xếp hạng](./phase-07-spec-017-bang-xep-hang.md) | Dũng | 1 | Pending |
| 8 | [Spec 018 tài liệu tham khảo](./phase-08-spec-018-tai-lieu-tham-khao.md) | Xuân Huy | 1 | Pending |
| 9 | [Phụ lục 014 bộ thẻ công khai + menu](./phase-09-phu-luc-014-bo-the-cong-khai-va-menu.md) | Xuân Huy | 1 | Pending |
| 10 | [Spec 024 AI sinh bài + hàng chờ duyệt](./phase-10-spec-024-ai-sinh-bai-va-hang-cho-duyet.md) | Xuân Huy | 1 | Pending |
| 11 | [Duyệt spec + hàng đợi task](./phase-11-duyet-spec-va-hang-doi-task.md) | Leader | 2–10, 12 | Pending |
| 12 | [Spec 022 chấm bài thuê + phụ lục 009 hồ sơ (042)](./phase-12-spec-022-giao-vien-cham-bai-thue.md) | Quốc Huy | 1 | Pending |

**Cập nhật 13/11:** 016 Video **vẫn gác** (bản nháp giữ trong worktree, không commit). Spec viết trong worktree `C:\CLHSK\cnhsk-spec-hoan-thien` nhánh `docs/spec-hoan-thien`. Thứ tự viết leader chốt: **017 → 018 → 019 → 020**. Tiến độ: 017 ✅ DRAFT (top 20, hiển thị chính 5) · 018 ✅ DRAFT (1 bảng, domain yaml, báo lỗi = audit_logs) · 019 ✅ DRAFT (không đổi mastery, 10 câu ngẫu nhiên, QUIZ_RETRY cho làm lại; đã sửa 017 `ref_type='QUIZ'`) · 020 ✅ DRAFT (vẫn nhắc khi đã học, giờ tròn, tắt 1 lần bấm token ký; cài đặt ở `auth`, job ở `learning`).

**Viết song song:** leader viết các spec cuốn chiếu, dựa trên H1–H14 ở phase 1 (không spec nào phải chờ spec khác). Thứ tự viết đề xuất = thứ tự người code rảnh sớm nhất: 020, 021 (Dũng) → 018, phụ lục 014, 024 (Xuân Huy) → 007/008 (Đạt) → 023 (Tường) → 022, phụ lục 009 (Quốc Huy, rảnh sau 013) → 019, 017. Chỉ trạng thái `APPROVED` chờ RFC đồng thuận.

## Ước tính (ngày công = spec + BE + FE, ±30%)

| Người | Mảng | Ngày công |
|---|---|---|
| Đạt | 007/008 (6 game) + 012/T-19 | 21 |
| Quốc Huy | 013 BE còn lại → 022 chấm thuê (base) + phụ lục 009 (042) | 20,5 |
| Tường | ⚿SettingsApi → 013/T-14 → 023 (078 sửa được) → 019 quiz | 25,5 |
| Dũng | 021 + 020 + 017 xếp hạng + nợ N+1 (011) | 24 |
| Xuân Huy | 018 + phụ lục 014 + 024 | 20,25 |

Tổng ~111 ngày công. Lịch: tuần 1 viết spec (song song) → tuần 2–4 code → ~4 tuần (Đạt nặng nhất).

## Tiêu chí hoàn thành plan

- [ ] RFC đợt hoàn thiện đồng thuận (H1–H14)
- [ ] 8 spec mới (017–024 trừ 016) + phụ lục 014, 009 + 008 ở trạng thái `APPROVED`, mỗi spec có `spec.md`, `tasks.md`, `questions.md`, `contracts/openapi-*.yaml`
- [ ] File `workflow-phu-thuoc-task-hoan-thien.md` (phase 11) liệt kê điểm khoá ⚿ và hàng đợi task
- [ ] Tài liệu gốc sửa theo RFC (feature-tree, CLAUDE.md, database.md AC-04)

## Câu hỏi mở (leader chốt)

1. ~~Cắt 022?~~ → **Giữ**, làm base trước (bài viết); **giá/tỷ lệ giáo viên đặt sau** ở 078 (leader 13/11)
2. ~~Thần Bút, Phi Đao~~ → **6 game như nhau**: lưu điểm, xếp hạng (leader 13/11, H11)
3. ~~078~~ → **sửa được lúc chạy**, bảng `shared.app_settings` + `SettingsApi` (leader 13/11, H13)
4. ~~024~~ → **hệ thống tự sinh từ câu sai + cây tri thức → duyệt → gắn KID → mới tới học viên** (leader 13/11, H12)
5. ~~Thưởng cuộc thi~~ → **không thưởng trong hệ thống**, liên hệ qua email (leader 13/11)
6. 024: tài khoản hệ thống đứng tên `service_requests` do job tạo (đề xuất: bootstrap ADMIN qua `SettingsApi`) — chốt khi viết spec

## Validation Log

### Session 1 — 2026-11-13

#### Verification Results
- Claims checked: 40 · Verified: 38 · Failed: 2 · Unverified: 0 · Tier: Full
- Failures:
  1. 021 gọi thẳng engine 012 — vi phạm ArchUnit `community_chi_duoc_goi_learning_qua_api` (`src/test/java/com/cnhsk/architecture/ModuleBoundaryTest.java`) → sửa: thêm `learning.api.ContestAttemptApi` (⚿K8)
  2. `shared.api` không tồn tại (`src/main/java/com/cnhsk/shared/` chỉ có audit/config/exception/security/util) → sửa: `com.cnhsk.shared.settings`
- Đã xác minh: `EntitlementApi.consume/refund`, `LedgerService.append`, `MasteryReader`, `RateLimiter`, `AuditService`, `ClaudeClient`/`StubClaudeClient`, `CommunityAuthors`, `ActiveTestApi`, `TargetedPracticeController`, `PaidFeature.GRADING`, các bảng/cột tái dùng, V5 `knowledge_points.can_do/name_zh`, migration tới V6.

#### Quyết định (4 câu)
| Câu | Trả lời | Ảnh hưởng |
|---|---|---|
| 021 ranh giới module | Thêm `learning.api.ContestAttemptApi` | phase 5, 11 (⚿K8), H14 |
| 021 một lượt/người | Partial unique index trong DB | phase 5 |
| 024 ai kích hoạt | **Hệ thống tự đánh giá câu sai, sinh theo dữ liệu cây tri thức**; không học viên bấm, không thu phí | phase 10, 6 (khoá ai-generate.*), H12 |
| Ai viết spec | **Leader viết như bình thường** | plan.md (bỏ Codex) |

#### Whole-Plan Consistency Sweep
- Đã rà toàn bộ plan.md + 12 phase: xoá "Codex", "học viên bấm AI sinh", `EntitlementApi` ở 024, `shared.api`, thưởng/⚿K4; H1–H10 → H1–H14.
- Mâu thuẫn còn lại: **0**. Câu mở còn lại (#6 tài khoản hệ thống cho job 024) không chặn — chốt khi viết spec 024.
