# Tasks: AI sinh câu luyện theo điểm yếu + hàng chờ duyệt — Spec #024

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-ai-practice.yaml`](contracts/openapi-ai-practice.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 11 task**, ≤ 4 giờ/task, một task một nhánh `spec/024-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 024 `APPROVED`. Người code: Quốc Huy. **Không migration.** Test chỉ dùng `StubClaudeClient`; **không commit `ANTHROPIC_API_KEY`**; chạy thật chỉ trên máy leader.
Khoá §0.1 đọc qua `SettingsApi` nếu 023/T-01 đã merge (T-01 thêm vào `SettingKey`), nếu chưa thì đọc yaml `cnhsk.ai-generate.*`.

## Đợt 1 · Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Cấu hình: khoá §0.1 (vào `SettingKey` nếu có, yaml mặc định), `cnhsk.ai.generate-model`, cron, timeout; mã lỗi §5 | `…/learning/aigen/AiGenerateConfig.java`, `application.yaml`, (`…/shared/settings/SettingKey.java`), `ErrorCode.java`, `GlobalExceptionHandlerTest` + test | §0.1, §5 | ✅ MERGED (#201, da5e1bc) |
| T-02 | `WeakKnowledgeSelector`: gom câu sai (KID chính + `error_kid` đã chọn) + ưu tiên `MasteryReader`, lọc kho/lô chờ/`can_do`, dọn lô `STALE` | `…/learning/aigen/WeakKnowledgeSelector.java` + test | FR-002…FR-004 · AT-03…AT-06 | ✅ MERGED (#217, 89d1952) |
| T-03 | `AiPromptBuilder` (khối cố định + khối nút từ DB, không dữ liệu người học) + `AiQuestionValidator` | `…/learning/aigen/{AiPromptBuilder,AiQuestionValidator}.java` + test | §0.3, FR-007 · AT-09, AT-10 | ✅ MERGED (#226, da863ad) |
| T-04 | `AiGenerationService` + `AiGenerationJob`: kiểm tài khoản đứng tên, tạo lô, gọi Claude ngoài transaction qua `TokenBudget`, lưu câu `PENDING_REVIEW` + KID, log | `…/learning/aigen/{AiGenerationService,AiGenerationJob}.java` + test | FR-001, FR-005, FR-006, FR-008…FR-010 · AT-01, AT-02, AT-07, AT-08 | ✅ MERGED (#234, 9ce518f) |
| T-05 | Duyệt: hàng chờ, duyệt/từ chối, duyệt hàng loạt có dấu "đã xem"; dấu "đã xem" khi mở `GET /api/admin/questions/{id}`; `PUT` câu AI chờ duyệt cho `CONTENT_EDITOR` (**báo người giữ 010**); `SecurityConfig` | `…/learning/aigen/AiReviewService.java`, `…/aigen/web/AiReviewController.java`, `…/learning/knowledge/QuestionAdminService.java`, `SecurityConfig.java` + test | FR-011…FR-016 · AT-11…AT-15 | ✅ MERGED — PR #241 |
| T-06 | Lịch sử lô + chạy thử (`ADMIN`); AT câu chưa duyệt không tới người học ở 011/012/019 | `…/learning/aigen/web/AiRunController.java`, `SecurityConfig.java` + test | FR-017…FR-019 · AT-16, AT-17 | ✅ MERGED — PR #255 |

## Đợt 2 · Frontend + hợp đồng

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-07 | FE SCR-058 + nút Duyệt/Từ chối trong SCR-059 khi câu là AI chờ duyệt; route `['TEACHER','CONTENT_EDITOR']`; menu quản trị | `web/features/ai-review/**`, `web/features/question-admin/**` (SCR-059), `routeAccess.ts`, `screenRegistry.ts` + test | FR-020 · AT-18 | ✅ MERGED — PR #256 |
| T-08 | FE trang "Lô AI" (`ADMIN`): lịch sử, token tháng, trạng thái bật/tắt, "Chạy thử" | `web/features/ai-runs/**`, `routeAccess.ts`, `screenRegistry.ts` + test | FR-018, FR-019 | ✅ MERGED — PR #258 |
| T-09 | Test hợp đồng `/v3/api-docs` so `openapi-ai-practice.yaml` (hai chiều path + method + status) | `t/contract/AiPracticeContractTest.java` | §13 · AT-19 | ✅ MERGED — PR #260 |
| T-10 | v1.1 chống gần trùng: chỉ dẫn prompt không lặp `samples`, câu trong lô khác nhau (không gửi thêm nội dung); `NEAR_DUPLICATE_STEM` (bỏ dấu câu/khoảng trắng, cùng đáp án đúng, cùng dãy số, Jaccard bigram ≥ 0,8, ≥ 8 code point); `existingStems` trả kèm `options` dạng text trong cùng một truy vấn, đáp án đúng đọc bằng Java | `…/aigen/AiPromptBuilder.java`, `AiQuestionValidator.java`, `WeakKnowledgeQueryRepository.java` + test | §0.3, FR-007 · AT-09 | ✅ MERGED (#285, 176dda1) — verify 7158 F0/E0/S0 |
| T-11 | v1.2 chỉ `TEACHER` duyệt/sửa: `SecurityConfig` (`/api/admin/ai-questions/**` và `PUT /api/admin/questions/{id}` → `TEACHER`), `AiReviewService`, `QuestionAdminService.requireEditable`; FE route/menu SCR-058 và nút sửa/duyệt SCR-059 chỉ `TEACHER`; mô tả hợp đồng | `SecurityConfig.java`, `…/aigen/AiReviewService.java`, `…/knowledge/QuestionAdminService.java`, `contracts/*.yaml`, `web/app/routeAccess.ts`, `web/shared/layout/navigation.ts`, `Scr059QuestionEditPage.tsx` + test | FR-011, FR-013, FR-020 · AT-15 | 🔄 leader làm |

## Thứ tự

```
T-01 ─► T-02 ─► T-03 ─► T-04 ─► T-06 ─► T-09
        T-05 (sau T-01, song song T-02…T-04)
T-07, T-08 song song từ đầu bằng dữ liệu mẫu; nối thật sau T-05 / T-06
```

**Rủi ro cao:** T-04 (gọi ngoài transaction, ngân sách, lô kẹt), T-05 (mở quyền sửa câu của 010 đúng phạm vi), T-06 (chứng minh câu chưa duyệt không lọt tới người học).
**Chạy thật:** leader đặt `ai-generate.system-user-id` ở 078 và `ANTHROPIC_API_KEY` trên máy chạy, hạ `min-wrong` nếu dữ liệu demo ít.
