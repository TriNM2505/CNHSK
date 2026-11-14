# Tasks: Cuộc thi theo khung giờ — Spec #021

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-contests.yaml`](contracts/openapi-contests.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 10 task**, ≤ 4 giờ/task, một task một nhánh `spec/021-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 021 `APPROVED`. Người code: Dũng. **Một migration V9** (leader cấp 2026-11-14). Không phụ thuộc 013, 023.

## Đợt 1 · Backend `learning` (⚿K8)

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | **⚿K8** `V9__contest_attempt_unique.sql`; `PracticeSource.CONTEST` (thêm cuối) + `PracticeApi`; `ActiveTestApi`/`existsActiveTest` thêm `CONTEST`; `EvidenceAllocator` bỏ qua `CONTEST`; interface `learning.api.ContestAttemptApi` + record (§10) chưa cài | `src/main/resources/db/migration/V9__…`, `…/learning/practice/PracticeSource.java`, `…/learning/api/{PracticeApi,ContestAttemptApi}.java`, `AttemptRepository.java`, `EvidenceAllocator.java`, `docs/reference/database.md` + test | FR-001, FR-008, §0.2, §10 · AT-01, AT-13 | ⬜ |
| T-02 | `ContestHandler` + cài `ContestAttemptApi.start/countAttempts/submit/exam` (khoá tư vấn, resume, câu chụp từ đề `DRAFT`, `ttlOverride`, ân hạn); chặn finish/abandon chung → `CONTEST_SUBMIT_REQUIRED` | `…/learning/contest/*`, `…/learning/practice/web/PracticeController.java` hoặc `PracticeService`, `ErrorCode.java` + test | FR-002…FR-005 · AT-02…AT-04, AT-06, AT-07 | ⬜ |
| T-03 | `ContestAttemptApi.myAttempt/ranking/rankOf/participants/setExcluded` (SQL theo `ix_att_ref_rank`) | `…/learning/contest/*` + test | FR-006, FR-007 · AT-11, AT-12 (phần learning) | ⬜ |

## Đợt 2 · Backend `community`

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-04 | Entity `Contest`, `ContestQueryService`, `PublicContestController` (danh sách, chi tiết, trạng thái lịch theo `Clock`) | `…/community/contest/**` + test | FR-009, FR-010 · AT-14 | ⬜ |
| T-05 | `ContestService` vào thi/nộp/sự kiện chuyển tab/kết quả; mã lỗi §5 | `…/community/contest/**`, `ErrorCode.java`, `GlobalExceptionHandlerTest` + test | FR-011…FR-014 · AT-05, AT-08…AT-10, AT-15 | ⬜ |
| T-06 | `ContestAdminService` + controller: tạo/sửa/công bố/huỷ, thí sinh + loại/giữ, công bố xếp hạng; `SecurityConfig` `/api/admin/contests/**` → `CONTENT_EDITOR` | `…/community/contest/**`, `SecurityConfig.java` + test | FR-017…FR-023 · AT-12, AT-16, AT-17 | ⬜ |
| T-07 | Người đạt giải: `AuthApi.contactEmails(Set<Long>)` (**module 001 — báo leader trước**), endpoint winners + `audit_logs` | `…/auth/api/AuthApi.java`, `…/auth/service/AuthApiService.java`, `…/community/contest/**` + test | FR-024 · AT-18, AT-19 | ⬜ |

## Đợt 3 · Frontend + hợp đồng

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-08 | FE người học SCR-048…051 (danh sách, chi tiết, làm bài có đồng hồ server + tự nộp + resume, kết quả); route `by-role`/`learner`; `SCREEN_NAMES` 4 màn + `AssistantPromptBuilderTest`; ẩn trợ lý ở SCR-050 (**báo Đạt — 015**); mục "Cuộc thi" nhóm Cộng đồng | `web/features/contests/**`, `web/app/routeAccess.ts`, `screenRegistry.ts`, `web/shared/layout/navigation.ts`, `web/features/assistant/components/AssistantHost.tsx`, `…/learning/assistant/AssistantPromptBuilder.java` + test | FR-015, FR-016 · AT-20 | ⬜ |
| T-09 | FE ban tổ chức SCR-070/071 (form giờ VN, khoá trường, bảng thí sinh, công bố, liên hệ người đạt giải) | `web/features/contest-admin/**`, `routeAccess.ts`, `screenRegistry.ts` + test | FR-025, FR-026 · AT-21 | ⬜ |
| T-10 | Test hợp đồng `/v3/api-docs` so `openapi-contests.yaml` (hai chiều path + method + status) | `t/contract/ContestContractTest.java` | §13 · AT-22 | ⬜ |

## Thứ tự

```
T-01(⚿K8, V9) ─► T-02 ─► T-03
                 └─► T-04 ─► T-05 ─► T-06 ─► T-07 ─► T-10
T-08, T-09 song song từ đầu bằng dữ liệu mẫu; nối thật sau T-05 / T-07
```

**Rủi ro cao:** T-02 (một lượt/người khi đua, hạn nộp + ân hạn, không lộ đáp án), T-05 (đủ người khi đua — đếm trong cùng khoá), T-07 (email chỉ ở một endpoint).
**Lưu ý migration:** V9 phải merge trước V10 của 023 (Flyway cấm chạy lệch thứ tự).
**Phối hợp 023:** khi 023/T-06 đã có `learning.api.ExamUsageGuard`, T-06 của 021 cài interface này (đề gắn cuộc thi `PUBLISHED` có `ends_at > now` → đang dùng) để 023 chặn lưu trữ đề. Nếu 021/T-06 làm trước, 023/T-06 thêm phần cài vào `community.contest`.
