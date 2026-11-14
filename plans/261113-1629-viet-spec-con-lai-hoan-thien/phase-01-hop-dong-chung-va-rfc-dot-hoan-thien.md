---
phase: 1
title: "Hợp đồng chung + RFC đợt hoàn thiện"
status: pending
priority: P1
dependencies: []
effort: "1 ngày soạn + leader duyệt"
---

# Phase 1: Hợp đồng chung + RFC đợt hoàn thiện

## Overview
Một tài liệu chốt **mọi quyết định dùng chung** trước khi 8 spec viết song song, để không spec nào phải chờ spec khác. Đầu ra: `plans/261113-1629-viet-spec-con-lai-hoan-thien/reports/rfc-261113-dot-hoan-thien.md` (khuôn như `plans/261102-2104-…/reports/rfc-261103-…md`).

## Context links
- `docs/reference/feature-tree.md` (thứ tự cắt), `docs/reference/database.md` §3, §11, §12, §13, §18
- `src/main/resources/db/migration/V1__core_schema.sql`, `V2__community.sql` (bảng có sẵn)
- `plans/261102-2104-dot-cuoi-cay-nang-luc-va-spec-010-015/workflow-phu-thuoc-task-010-015.md` (luật L1–L4)
- `.specify/memory/constitution.md` (AC-03, AC-04, AC-07, AC-09)

## Quyết định chung (H1–H14) — các spec viết theo đây

| # | Quyết định | Áp dụng |
|---|---|---|
| H1 | **Bỏ mobile.** Feature 6.5 cắt; ADR-005 lịch sử; UC-013 (WebView) bỏ; `study_events.channel` chỉ `WEB`/`EMAIL`, không `PUSH` | 020, 007, tài liệu gốc |
| H2 | **Gác 016 Video** (giữ bảng `videos`, không code). **Giữ 022 chấm thuê** (leader 13/11) trên `service_requests` `GRADING` + `TEACHER_PAYOUT` có sẵn | 016, 022 |
| H3 | **Chỉ 2 bảng mới:** `learning.reference_items` (018) và `shared.app_settings` (023, màn 078 sửa được — leader 13/11). AC-04: 33 → 35 bảng. Mọi spec khác dùng bảng ở bảng "tái dùng" trong `plan.md` | tất cả |
| H13 | **Cấu hình nghiệp vụ đọc qua `SettingsApi` (⚿K7, package `com.cnhsk.shared.settings`)**; giá/tỷ lệ chấm thuê đặt sau ở 078 (leader 13/11) | 023, 022, 020, 019, 013/014/015 |
| H4 | **Migration theo thứ tự merge, bắt đầu V7.** Thực tế (cập nhật 2026-11-14): V7 = 018 `reference_items`, V8 = 017 index `ix_att_ref_rank`, V9 = 021 index `uq_contest_attempt`, V10 = 023 `app_settings`; spec khác chỉ thêm migration khi cần index/CHECK — xin số ở leader trước khi mở PR. Chỉ leader chạy Neon | 018, ai cần index |
| H5 | **Không đổi CHECK của 5 bảng "không đụng"** (`credit_transactions`, `attempt_items`, `user_progress`, `knowledge_points`, `question_knowledge_points`). **Cuộc thi không có thưởng trong hệ thống** — giải trao ngoài hệ thống qua liên hệ email (leader 13/11) | 021 |
| H11 | **6 game như nhau** (lưu điểm, xếp hạng, mastery) — phụ lục 008 thêm Thần Bút, Phi Đao (leader 13/11) | 007/008, 017 |
| H12 | **Câu AI: hệ thống tự sinh** từ phân tích câu sai + dữ liệu cây tri thức trong DB → **duyệt → gắn KID → mới tới học viên**; không thu phí học viên (leader 13/11) | 024 |
| H14 | **Community gọi learning chỉ qua `learning.api`** (ArchUnit hiện hành): 021 thêm `ContestAttemptApi`; 007 ghi kết quả game qua `PracticeApi` | 021, 007 |
| H6 | **Bảng xếp hạng đọc thẳng `attempts`** (kind `QUIZ`/`GAME`, `status='SUBMITTED'`) trong module `learning` — **không gọi code của 019/007**. Hợp đồng dữ liệu: quiz ghi `ref_type='QUIZ'` (engine ghi tên source), `ref_id=topic_id`, làm lại = `QUIZ_RETRY`; game ghi `game_code`; `score` BIGINT do server tính | 017, 019, 007 |
| H7 | **Enum chung một PR sớm (⚿K1):** `PracticeSource` thêm `QUIZ`, `CONTEST`; `ItemType` không đổi (dùng `QUESTION_CHOICE`); `PaidFeature` giữ `AI_GENERATE`. Một người (Quốc Huy) mở PR nhỏ đầu tuần 2, các spec khác chỉ tham chiếu | 019, 021 |
| H8 | **File dùng chung merge lần lượt (L2 cũ):** `ErrorCode.java` (mã mới ngay trước `INTERNAL_ERROR`, theo thứ tự merge), `SecurityConfig.java`, `routeAccess.ts`, `screenRegistry.ts`, `navigation.ts`, `application.yaml`. Mỗi spec liệt kê ở §0.4 "file dùng chung bị sửa" | tất cả |
| H9 | **Chống gian lận dùng lại hạ tầng có sẵn:** `served_items` snapshot, server chấm, `suspicious`, `ActiveTestApi` chặn trợ lý khi đang thi, `RateLimiter`; điểm client gửi không bao giờ được lưu (LESSON-003) | 007/008, 019, 021 |
| H10 | **Không redesign FE đợt này:** màn mới dùng `shared/ui` + token `design.md` hiện có; không sửa `shared/ui`/layout trong PR tính năng (cần thì báo leader) | tất cả |

## Sửa tài liệu gốc (leader làm sau khi RFC đồng thuận)
- `feature-tree.md`: 6.5 cắt, 1.6 gác, cập nhật bảng trạng thái và thứ tự cắt (bỏ mục "chấm thuê" khỏi danh sách cắt).
- `CLAUDE.md`: bỏ mobile khỏi kiến trúc/nhóm; trạng thái hiện tại.
- `database.md` §3, §11, §18: thêm `app_settings`, `reference_items`, AC-04 = 35.
- `screens.json`/`design.md` §5: đánh dấu SCR-037/038 (gác).

## Implementation Steps
1. Soạn RFC theo bảng H1–H14, mỗi mục ghi: hiện đang ghi · đề xuất · vì sao · spec ảnh hưởng.
2. Gửi leader + nhóm; đồng thuận → cập nhật `plan.md` status.
3. Leader sửa tài liệu gốc trong một PR docs riêng.

## Success Criteria
- [ ] RFC có đủ H1–H14, mỗi mục chỉ ra spec ảnh hưởng
- [ ] Leader chốt 5 câu hỏi mở trong `plan.md`
- [ ] Không mục nào yêu cầu sửa code đã merge 001–015

## Risk Assessment
- RFC chậm → spec vẫn viết song song được (dựa H1–H14), chỉ `APPROVED` chậm. Giảm: leader duyệt trong 1 ngày.
- Đổi ý H6 sau khi 017 code → 017 phải viết lại. Giảm: H6 là hợp đồng dữ liệu, ghi rõ vào 007/019 `spec.md` §10 Móc nối.
