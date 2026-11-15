# Tasks: Quản trị đề thi, tiên quyết chủ đề và cài đặt hệ thống — Spec #023

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-content-admin.yaml`](contracts/openapi-content-admin.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 12 task**, ≤ 4 giờ/task, một task một nhánh `spec/023-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 023 `APPROVED`. Người code: Xuân Huy. **Migration V10** — chỉ merge sau V9 (021/T-01).
Sửa code module khác (013/014/015/019, 003) → mỗi chỗ một task nhỏ, **báo người giữ module**, không đổi hành vi mặc định.

## Đợt 1 · Cài đặt hệ thống (⚿K7 — làm đầu tiên)

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | **⚿K7** `V10__app_settings.sql`; `SettingKey` (§0.2), `SettingsApi` (cache 30 s, mặc định yaml, lỗi DB → mặc định); test cấm khoá nhạy cảm; `database.md` (bảng + V10) | `src/main/resources/db/migration/V10__app_settings.sql`, `…/shared/settings/{SettingKey,SettingsApi,SettingsService}.java`, `docs/reference/database.md` + test | FR-001…FR-003, §0.1 · AT-01…AT-03 | ✅ G2: full verify 5452 F0/E0/S0, LINE 96,943049%; Draft chờ leader review/merge |
| T-02 | API `ADMIN`: danh sách, đổi, về mặc định, lịch sử (`audit_logs`); `SecurityConfig`; mã `SETTING_*` | `…/shared/settings/SettingsController.java`, `SecurityConfig.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` + test | FR-004…FR-007 · AT-04, AT-05, AT-08 | ✅ G2 sau rebase `dc8adb4`: targeted 208, hồi quy + boundary 130, full verify 5833 F0/E0/S0; LINE 97,037726%; Draft chờ leader review/merge |
| T-03 | Chuyển chỗ đọc 013 (`billing.free-monthly-limit`) + 015 (`assistant.daily-limit`, `ai.monthly-token-budget`) — **báo Đạt (015)** | `…/learning/credit/EntitlementService.java`, `…/learning/assistant/AssistantDailyLimit.java`, chỗ đọc ngân sách token + test | FR-009 · AT-06, AT-07 | ⬜ |
| T-04 | Chuyển chỗ đọc 014 (6 khoá thư viện) + 019 (2 khoá quiz); nếu 021/022 đã merge mà còn đọc yaml thì chuyển luôn (FR-010) | `…/learning/library/*`, `…/learning/quiz/QuizConfig.java` (+ 021/022 nếu có) + test | FR-009, FR-010 · AT-07 | ⬜ |

## Đợt 2 · Đề thi

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-05 | Danh sách, tạo, chi tiết (`etag`, `publicationErrors`), sửa nháp, nhân bản; mã `EXAM_CODE_TAKEN`, `EXAM_NOT_EDITABLE`, `EDIT_CONFLICT`; `SecurityConfig` `/api/admin/exams/**` | `…/learning/examadmin/**`, `SecurityConfig.java`, `ErrorCode.java` + test | FR-011…FR-014, FR-017 · AT-09, AT-10, AT-14 | ✅ G2: targeted 144 F0/E0/S0; full verify 5620 F0/E0/S0, LINE 97,001216%; Draft chờ leader review/merge |
| T-06 | `ExamPublicationValidator` + công bố; lưu trữ + `learning.api.ExamUsageGuard`; mã `EXAM_PUBLISH_INVALID`, `EXAM_IN_USE` (021 cài guard ở task của 021) | `…/learning/examadmin/**`, `…/learning/api/ExamUsageGuard.java`, `ErrorCode.java` + test | FR-015, FR-016, FR-018 · AT-11…AT-13 | ✅ G2 sau sửa fixture/rebase `9e6773b`: targeted 175, hồi quy + boundary 103, full verify một lần 5983 F0/E0/S0; LINE 97,059209%; Draft chờ leader review/merge |

## Đợt 3 · Tiên quyết chủ đề + cổng

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-07 | Cổng khoá ở 003: `LOCKED`/`lockedBy` một truy vấn, `TOPIC_LOCKED` ở study/practice/tests (resume vẫn được); sửa `openapi-practice-vocab.yaml` + 5 test `learning.practice.*` đọc YAML **cùng PR** | `…/learning/vocab/{TopicQueryRepository,TopicQueryService,TopicLockQuery}.java`, `…/vocab/web/TopicController.java`, `specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml`, `ErrorCode.java` + test | FR-022…FR-025 · AT-17…AT-19 | ✅ G2 trên base `8e4c390`: targeted 387, contract 12, hồi quy + boundary 80; full verify lần hai 6156 F0/E0/S0, LINE 97,051471%; lượt full đầu dừng F2 contract 018/020 đã ghi evidence; Draft chờ leader review/merge |
| T-08 | API tiên quyết: danh sách, đặt (DFS chống vòng, còn gốc, khoá tư vấn, cảnh báo cấp, audit); `SecurityConfig` `/api/admin/topics/**` | `…/learning/vocab/TopicPrerequisiteService.java`, `…/vocab/web/TopicAdminController.java`, `SecurityConfig.java`, `ErrorCode.java` + test | FR-020, FR-021 · AT-15, AT-16 | ✅ G2 base `00088af`, phụ thuộc #203: targeted 93, hồi quy + boundary 634, full verify 6216 F0/E0/S0; LINE 97,044617%; targeted đầu F2 assertion đã ghi evidence; Draft chờ leader review/merge |

## Đợt 4 · Frontend + hợp đồng

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-09 | FE SCR-078 (bảng theo nhóm, sửa có lý do, về mặc định, lịch sử, token đã dùng) | `web/features/admin-settings/**`, `routeAccess.ts`, `screenRegistry.ts`, menu quản trị + test | FR-008 · AT-20 | ⬜ |
| T-10 | FE SCR-065/066 (bảng đề, trình sửa phần + chọn câu từ `/api/admin/questions`, kéo thả, lỗi công bố, 409) | `web/features/exam-admin/**`, `routeAccess.ts`, `screenRegistry.ts`, menu + test | FR-019 · AT-20 | ⬜ |
| T-11 | FE SCR-067 + hiển thị khoá ở SCR-011/012 (003) | `web/features/topic-admin/**`, `web/features/vocab/**` (thẻ chủ đề), `routeAccess.ts`, `screenRegistry.ts` + test | FR-026 · AT-20 | ⬜ |
| T-12 | Test hợp đồng `/v3/api-docs` so `openapi-content-admin.yaml` (hai chiều path + method + status) | `t/contract/ContentAdminContractTest.java` | §13 · AT-21 | ✅ G2 base `e681945`, đúng 3 file: targeted 6, hồi quy + boundary 21, full verify 6457 F0/E0/S0; LINE 97,115925% (15557/16019); 10 path/13 operation khớp, không dùng ngoại lệ; Draft chờ leader review/merge |

## Thứ tự

```
T-01(⚿K7, V10 sau V9) ─► T-02 ─► T-03, T-04 ─► T-09
T-05 ─► T-06 ─► T-10            (song song với đợt 1)
T-07 ─► T-08 ─► T-11            (song song với đợt 1)
T-12 sau T-02, T-06, T-08
```

**Rủi ro cao:** T-01 (cache + mặc định đúng yaml), T-03/T-04 (không đổi hành vi module khác), T-07 (đổi hợp đồng 003 có nhiều test đọc), T-08 (chống vòng khi hai người sửa song song).
